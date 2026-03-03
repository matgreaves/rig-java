package io.github.matgreaves.rig.client;

/**
 * Marker interface implemented by all service type builders.
 * Sealed — only types in this package implement it.
 */
public sealed interface ServiceDef permits
        GoDef, FuncDef, ProcessDef, ContainerDef, PostgresDef, TemporalDef, RedisDef, S3Def, SqsDef, CustomDef {
}
