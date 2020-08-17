package com.github.triplet.gradle.play.internal

import com.github.triplet.gradle.androidpublisher.PlayPublisher
import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.github.triplet.gradle.play.PlayPublisherExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.Serializable
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

internal fun PlayPublisherExtension.toConfig() = PlayExtensionConfig(
        enabled.get(),
        serviceAccountCredentials.orNull?.asFile,
        defaultToAppBundles.get(),
        commit.get(),
        fromTrack.orNull,
        track.get(),
        promoteTrack.orNull,
        userFraction.orNull,
        updatePriority.orNull,
        releaseStatus.orNull,
        releaseName.orNull,
        resolutionStrategy.get(),
        retain.artifacts.orNull,
        retain.mainObb.orNull,
        retain.patchObb.orNull
)

internal fun PlayExtensionConfig.credentialStream(): InputStream {
    return serviceAccountCredentials?.inputStream() ?: ByteArrayInputStream(
            System.getenv(PlayPublisher.CREDENTIAL_ENV_VAR).toByteArray())
}

internal fun mergeExtensions(extensions: List<ExtensionMergeHolder>): PlayPublisherExtension {
    requireNotNull(extensions.isNotEmpty()) { "At least one extension must be provided." }
    if (extensions.size == 1) return extensions.single().original

    val extensionsWithRootInitialization = extensions + extensions.last()
    return mergeExtensionsInternal(extensionsWithRootInitialization)
}

private fun mergeExtensionsInternal(
        extensions: List<ExtensionMergeHolder>
): PlayPublisherExtension {
    for (i in 1 until extensions.size) {
        val parentCopy = extensions[i].uninitializedCopy
        val (child, childCopy) = extensions[i - 1]

        PlayPublisherExtension::class.declaredMemberProperties
                .linkProperties(parentCopy, child, childCopy)
        PlayPublisherExtension.Retain::class.declaredMemberProperties
                .linkProperties(parentCopy.retain, child.retain, childCopy.retain)
    }

    return extensions.first().uninitializedCopy
}

private fun <T> Collection<KProperty1<T, *>>.linkProperties(parent: T, child: T, childCopy: T) {
    for (property in this) {
        if (property.name == "name") continue

        val value = property.get(childCopy)
        @Suppress("UNCHECKED_CAST")
        if (value is Property<*>) {
            val originalProperty = property.get(child) as Property<Nothing>
            val parentFallback = property.get(parent) as Property<Nothing>
            if (value !== parentFallback) {
                value.set(originalProperty.orElse(parentFallback))
            } else {
                value.set(originalProperty)
            }
        } else if (value is ListProperty<*>) {
            val originalProperty = property.get(child) as ListProperty<Nothing>
            val parentFallback = property.get(parent) as ListProperty<Nothing>
            if (value !== parentFallback) {
                value.set(originalProperty.map {
                    it.takeUnless { it.isEmpty() }.sneakyNull()
                }.orElse(parentFallback))
            } else {
                value.set(originalProperty)
            }
        }
    }
}

// TODO(asaveau): remove after https://github.com/gradle/gradle/issues/12388
@Suppress("UNCHECKED_CAST")
private fun <T> T?.sneakyNull() = this as T

internal abstract class CliPlayPublisherExtension : PlayPublisherExtension("cliOptions")

internal data class PlayExtensionConfig(
        val enabled: Boolean,
        val serviceAccountCredentials: File?,
        val defaultToAppBundles: Boolean,
        val commit: Boolean,
        val fromTrack: String?,
        val track: String,
        val promoteTrack: String?,
        val userFraction: Double?,
        val updatePriority: Int?,
        val releaseStatus: ReleaseStatus?,
        val releaseName: String?,
        val resolutionStrategy: ResolutionStrategy,
        val retainArtifacts: List<Long>?,
        val retainMainObb: Int?,
        val retainPatchObb: Int?
) : Serializable

internal data class ExtensionMergeHolder(
        val original: PlayPublisherExtension,
        val uninitializedCopy: PlayPublisherExtension
)
