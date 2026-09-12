package com.algolens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One parameter of a judge-enabled problem's target method: its declared type and name. */
@Embeddable
public class ParamSpec {

    @Column(name = "param_type", length = 64)
    private String type;

    @Column(name = "param_name", length = 64)
    private String name;

    protected ParamSpec() {
        // for JPA
    }

    public ParamSpec(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }
}
