package com.orgasm.backend.domain;

import com.github.f4b6a3.tsid.TsidCreator;

public final class TsidGenerator {

    private TsidGenerator() {}

    public static String generate(String prefix) {
        return prefix + "_" + TsidCreator.getTsid().toLowerCase();
    }
}
