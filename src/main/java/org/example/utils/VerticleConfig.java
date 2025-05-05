package org.example.utils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

public class VerticleConfig
{
    public final Class<? extends AbstractVerticle> verticleClass;
    public final DeploymentOptions options;

    public VerticleConfig(Class<? extends AbstractVerticle> verticleClass, DeploymentOptions options)
    {
        this.verticleClass = verticleClass;
        this.options = options;
    }
}
