package org.example.utils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

/**
 * Class to encapsulate the configuration details for a Verticle deployment.
 * This class holds the Verticle class type and its deployment options.
 */
public record VerticleConfig(Class<? extends AbstractVerticle> verticleClass, DeploymentOptions options) {}
