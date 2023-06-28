package io.github.mattidragon.configloader.impl;

import com.squareup.javapoet.*;
import io.determann.shadow.api.ShadowApi;
import io.determann.shadow.api.ShadowProcessor;
import io.determann.shadow.api.TypeKind;
import io.determann.shadow.api.shadow.Declared;
import io.determann.shadow.api.shadow.Record;
import io.determann.shadow.api.shadow.RecordComponent;
import io.determann.shadow.api.shadow.Shadow;
import io.determann.shadow.api.wrapper.AnnotationValueTypeChooser;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.io.UncheckedIOException;

import static io.determann.shadow.api.ShadowApi.convert;

@SupportedAnnotationTypes(ConfigLoaderAnnotationProcessor.GENERATE_MUTABLE_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ConfigLoaderAnnotationProcessor extends ShadowProcessor {
    public static final String GENERATE_MUTABLE_ANNOTATION = "io.github.mattidragon.configloader.api.GenerateMutable";

    @Override
    public void process(ShadowApi api) {
        var annotated = api.getAnnotatedWith(GENERATE_MUTABLE_ANNOTATION);
        annotated.declaredTypes() // Everything should be declared
                .stream()
                .filter(shadow -> !shadow.isTypeKind(TypeKind.RECORD))
                .forEach(shadow -> api.logErrorAt(shadow, "@GenerateMutable can only be applied to records"));

        for (var record : annotated.records()) {
            var sourceInterface = getMutable(record).nestedClass("Source");
            if (record.getElement()
                    .getInterfaces()
                    .stream()
                    .map(DeclaredType.class::cast)
                    .map(DeclaredType::asElement)
                    .map(TypeElement.class::cast)
                    // We check both with and without package, because if the source class wasn't imported explicitly then the compiler doesn't know it's package
                    .noneMatch(element -> element.getQualifiedName().contentEquals(sourceInterface.canonicalName())
                                          || element.getQualifiedName().contentEquals(String.join(".", sourceInterface.simpleNames())))) {
                api.logErrorAt(record, "Records with generated mutable versions must implement source interface (%s)".formatted(sourceInterface.canonicalName()));
            }

            // Skip inner classes where outer class is mutable because it's handled separately
            if (record.getElement().getEnclosingElement() instanceof TypeElement declared
                && hasMutable(api.getShadowFactory().shadowFromElement(declared))) {
                continue;
            }
            writeMutable(record);
        }
    }

    private void writeMutable(Record record) {
        try {
            JavaFile.builder(record.getPackage().getQualifiedName(), generateMutable(record, false))
                    .indent("    ")
                    .build()
                    .writeTo(record.getApi().getJdkApiContext().getProcessingEnv().getFiler());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TypeSpec generateMutable(Record record, boolean inner) {
        var useGetters = record.getDirectUsageOfOrThrow(record.getApi().getAnnotationOrThrow(GENERATE_MUTABLE_ANNOTATION))
                .getValueOrThrow("encapsulateFields")
                .asBoolean();
        var components = record.getRecordComponents();
        var mutableName = "Mutable" + record.getSimpleName();
        var recordTypeName = TypeName.get(record.getMirror());

        var fields = components.stream()
                .map(component -> FieldSpec.builder(getMutableOrSelf(component.getType()), component.getSimpleName())
                        .addModifiers(useGetters ? Modifier.PRIVATE : Modifier.PUBLIC)
                        .build())
                .toList();
        var getters = components.stream()
                .map(component -> MethodSpec.methodBuilder(getGetterName(component))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(getMutableOrSelf(component.getType()))
                        .addStatement("return this.$L", component.getSimpleName())
                        .build())
                .toList();
        var setters = components.stream()
                .map(component -> MethodSpec.methodBuilder(getSetterName(component))
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(getMutableOrSelf(component.getType()), component.getSimpleName())
                        .addStatement("this.$L = $L", component.getSimpleName(), component.getSimpleName())
                        .build())
                .toList();

        var constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(getMutable(record).nestedClass("Source"), "immutable")
                .addCode(components.stream()
                        .map(component -> {
                            var hasMutable = convert(component.getType()).toDeclared().map(this::hasMutable).orElse(false);
                            if (hasMutable) return CodeBlock.of("this.$L = immutable.$L().toMutable();", component.getSimpleName(), component.getSimpleName());
                            return CodeBlock.of("this.$L = immutable.$L();", component.getSimpleName(), component.getSimpleName());
                        })
                        .collect(CodeBlock.joining("\n")));
        var constructor = constructorBuilder.build();

        var toImmutable = MethodSpec.methodBuilder("toImmutable")
                .addModifiers(Modifier.PUBLIC)
                .returns(recordTypeName)
                .addStatement(CodeBlock.builder()
                        .add("return new $T(", recordTypeName)
                        .add(components.stream()
                                .map(component -> {
                                    var hasMutable = convert(component.getType()).toDeclared().map(this::hasMutable).orElse(false);
                                    if (hasMutable) return CodeBlock.of("this.$L.toImmutable()", component.getSimpleName());
                                    return CodeBlock.of("this.$L", component.getSimpleName());
                                }).collect(CodeBlock.joining(", ")))
                        .add(")")
                        .build())
                .build();

        var innerMutables = ElementFilter.typesIn(record.getMirror().asElement().getEnclosedElements())
                .stream()
                .map(record.getApi().getShadowFactory()::<Record>shadowFromElement)
                .map(innerRecord -> generateMutable(innerRecord, true))
                .toList();

        var accessInterface = createSourceInterface(record);
        var classBuilder = TypeSpec.classBuilder(mutableName);

        classBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        if (inner) classBuilder.addModifiers(Modifier.STATIC);

        classBuilder.addFields(fields);

        classBuilder.addMethod(constructor).addMethod(toImmutable);
        if (useGetters) classBuilder.addMethods(getters).addMethods(setters);

        classBuilder.addTypes(innerMutables).addType(accessInterface);

        return classBuilder.build();
    }

    private static String getGetterName(RecordComponent component) {
        var fancy = component.getRecord().getDirectUsageOf(component.getApi().getAnnotationOrThrow(GENERATE_MUTABLE_ANNOTATION))
                .map(annotationUsage -> annotationUsage.getValueOrThrow("useFancyMethodNames"))
                .map(AnnotationValueTypeChooser::asBoolean)
                .orElse(false);


        String simpleName = component.getSimpleName();
        return fancy ? "get" + Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1) : simpleName;
    }

    private static String getSetterName(RecordComponent component) {
        var fancy = component.getRecord().getDirectUsageOf(component.getApi().getAnnotationOrThrow(GENERATE_MUTABLE_ANNOTATION))
                .map(annotationUsage -> annotationUsage.getValueOrThrow("useFancyMethodNames"))
                .map(AnnotationValueTypeChooser::asBoolean)
                .orElse(false);


        String simpleName = component.getSimpleName();
        return fancy ? "set" + Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1) : simpleName;
    }

    private TypeSpec createSourceInterface(Record record) {
        var accessors = record.getRecordComponents()
                .stream()
                .map(RecordComponent::getGetter)
                .map(getter -> MethodSpec.methodBuilder(getter.getSimpleName())
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(TypeName.get(getter.getReturnType().getMirror()))
                        .build())
                .toList();

        var toMutable = MethodSpec.methodBuilder("toMutable")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addStatement("return new $T(this)", getMutableOrSelf(record))
                .returns(getMutableOrSelf(record))
                .build();

        return TypeSpec.interfaceBuilder("Source")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.SEALED)
                .addMethods(accessors)
                .addMethod(toMutable)
                .addPermittedSubclass(TypeName.get(record.getMirror()))
                .build();
    }

    private boolean hasMutable(Declared declared) {
        return declared.getDirectUsageOf(declared.getApi().getAnnotationOrThrow("io.github.mattidragon.configloader.api.GenerateMutable")).isPresent();
    }

    private TypeName getMutableOrSelf(Shadow<? extends TypeMirror> shadow) {
        if (shadow instanceof Declared declared && hasMutable(declared)) {
            return getMutable(declared);
        } else {
            return TypeName.get(shadow.getMirror());
        }
    }

    private ClassName getMutable(Declared declared) {
        if (declared.getElement().getEnclosingElement() instanceof TypeElement typeElement) {
            Declared outer = declared.getApi().getShadowFactory().shadowFromElement(typeElement);
            if (hasMutable(outer)) {
                return getMutable(outer).nestedClass("Mutable" + declared.getSimpleName());
            }
        }
        return ClassName.get(declared.getPackage().getQualifiedName(), "Mutable" + declared.getSimpleName());
    }
}
