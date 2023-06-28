package io.github.mattidragon.configloader.test.ap;

import io.github.mattidragon.configloader.api.GenerateMutable;

import java.util.List;

@GenerateMutable
public record TestRecord(String a, String b, List<? extends AutoCloseable> x, InnerTestRecord inner) implements MutableTestRecord.Source {

    @GenerateMutable(encapsulateFields = false)
    public record InnerTestRecord(String a, int b) implements MutableTestRecord.MutableInnerTestRecord.Source {

    }
}
