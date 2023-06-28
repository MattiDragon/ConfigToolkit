# ConfigToolkit
ConfigToolkit is a small library mod that includes useful tools for building codec based configs. 
Primarily built for personal use, but probably useful to others as well.

## Usage
You can add ConfigToolkit to your project from jitpack. 
Add the following to your `repositories` block in your `build.gradle` if you don't already have it:
```gradle
maven {
    url "https://jitpack.io"
}
```
Then you can add ConfigToolkit as a dependency in the `dependencies` block like this:
```
dependencies {
    // ... other stuff
    modImplementation(include(annotationProcessor("com.github.mattidragon:configtoolkit:VERSION")))
}
```


## Features
### ConfigManager
The `ConfigManager` interface handles loading and saving of your config for you, using codecs and gson.
While somewhat simple to implement yourself, the implementation here handles everything for you.

It disables the config file if datagen or automatic gametest is running, 
meaning that your CI will never break due to an outdated config file.

Create one using the `ConfigManager.create()` method. Pass in a codec, default value and string id. 
It's recommended to call `get` on it once during the mod initializer to make sure the config loads during startup.

### DefaultingFieldCodec
`DefaultingFieldCodec` provides a codec similar to `Codec#fieldOf`, but offers one main advantage:
It has a default value for when the option is missing from the config. 
This allows you to safely add values to your config without users having to worry about updating their config.

While there are other ways to have default values when using codecs, 
this is the only way to do so without also having a default in the case of invalid values.
This is important because you don't want your users getting their settings overridden, even if they are invalid.
It's better to show an error and let them fix it.

### GenerateMutable
The `GenerateMutable` annotation is a really useful tool when using records to hold config options. 
Many config UI libraries, like YACL, are built around mutating values, 
while codecs are naturally immutable (which is good for config storage).
This annotation allows you to generate a mutable version of your records automatically using annotation processing.

To use `GenerateMutable` you first need to ensure that ConfigToolkit is on the annotation processor classpath.
If it isn't the annotation won't do anything. To create a mutable version of your records, 
first add the annotation and configure any options you like on it, then compile your mod.
You'll get a compiler error about having to implement a `Source` interface on your record.
Implement the interface, adding a `toMutable` method to your record.

There are several limitations to this system:
* It can't currently deal with generic records, but generics in component types should work fine.
* It has to place the generated classes at the top level, even if your record is an inner class, 
  unless the outer class also has a generated mutable version.
* You will have to recompile your project to see changes update on the mutable versions,
  but this is usually not that big of an issue.
