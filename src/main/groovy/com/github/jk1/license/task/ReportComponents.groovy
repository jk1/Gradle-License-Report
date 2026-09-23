/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.task

import com.github.jk1.license.util.Files
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest

/**
 * The inputs of renderers, importers and filters of a {@link ReportTask}.
 *
 * The built-in ones are {@link org.gradle.api.tasks.Nested nested} into the task, so Gradle tracks and validates
 * their annotated properties. Custom ones are fingerprinted leniently instead, as nesting them would make Gradle fail
 * the build for every property they don't annotate: only their type and their properties annotated as
 * {@link Input} or file inputs count. File inputs are fingerprinted by their content, rather than declared as file
 * inputs of the task: Gradle would then require the tasks generating them to be declared as dependencies, failing
 * builds that don't. Of Gradle's file normalization, only {@link PathSensitive} is honored, defaulting to
 * {@link PathSensitivity#RELATIVE} so that the fingerprint does not depend on the location of the build.
 */
class ReportComponents {
    private static final Logger LOGGER = Logging.getLogger(ReportComponents.class)
    private static final List<Class<? extends Annotation>> VALUE_INPUTS = [Input]
    private static final List<Class<? extends Annotation>> FILE_INPUTS = [InputFile, InputFiles, InputDirectory, Classpath, CompileClasspath]

    static boolean isBuiltIn(Object component) {
        Class<?> type = component.getClass()
        type.classLoader == ReportComponents.classLoader && type.name.startsWith('com.github.jk1.license.')
    }

    static <T> List<T> builtIn(List<T> components) {
        components.findAll { isBuiltIn(it) }
    }

    /**
     * The type, {@link Input} values and file input contents of the custom components, in order.
     *
     * @param toFiles resolves the value of a file input property to the files it denotes
     */
    static List<String> customInputValues(List<?> components, Closure<FileCollection> toFiles) {
        components.findAll { !isBuiltIn(it) }.collectMany { component ->
            String type = component.getClass().name
            [type] +
                annotatedProperties(component, VALUE_INPUTS).collect { name, property ->
                    "$type.$name=${fingerprint(property.value)}".toString()
                } +
                annotatedProperties(component, FILE_INPUTS).collect { name, property ->
                    "$type.$name=${fingerprintFiles(property.value, pathSensitivity(property.element), toFiles)}".toString()
                }
        }
    }

    /** Values of the properties annotated with any of the given annotations, on their getter or field. */
    private static Map<String, InputProperty> annotatedProperties(Object component, List<Class<? extends Annotation>> annotations) {
        Map<String, InputProperty> properties = new TreeMap<>()
        component.getClass().methods
            .findAll { Method it -> isGetter(it) && annotations.any { annotation -> it.isAnnotationPresent(annotation) } }
            .each { Method getter ->
                getter.accessible = true
                properties.put(propertyName(getter), new InputProperty(read(component, getter.name) { getter.invoke(component) }, getter))
            }
        for (Class<?> type = component.getClass(); type != null && type != Object; type = type.superclass) {
            type.declaredFields
                .findAll { Field it -> !Modifier.isStatic(it.modifiers) && !it.synthetic && annotations.any { annotation -> it.isAnnotationPresent(annotation) } }
                .each { Field field ->
                    field.accessible = true
                    properties.putIfAbsent(field.name, new InputProperty(read(component, field.name) { field.get(component) }, field))
                }
        }
        properties
    }

    /** Reads a property leniently: failing to fingerprint a custom component must not fail the build. */
    private static Object read(Object component, String name, Closure<Object> reader) {
        try {
            return reader()
        } catch (Exception e) {
            LOGGER.info("Could not read input property '$name' of ${component.getClass().name}, it is ignored for up-to-date checks", e)
            return '<unreadable>'
        }
    }

    /** Relative paths and content hashes of the files, of all files within directories. */
    private static String fingerprintFiles(Object value, PathSensitivity sensitivity, Closure<FileCollection> toFiles) {
        if (value == null) {
            return 'null'
        }
        List<String> files = []
        toFiles(value).asFileTree.visit { FileVisitDetails details ->
            if (!details.directory) {
                files << "${normalizedPath(details, sensitivity)}:${Files.contentHash(details.file)}".toString()
            }
        }
        '[' + files.sort().join(', ') + ']'
    }

    private static String normalizedPath(FileVisitDetails details, PathSensitivity sensitivity) {
        switch (sensitivity) {
            case PathSensitivity.ABSOLUTE: return details.file.absolutePath
            case PathSensitivity.NAME_ONLY: return details.name
            case PathSensitivity.NONE: return ''
            default: return details.relativePath.pathString
        }
    }

    private static PathSensitivity pathSensitivity(AnnotatedElement element) {
        element.getAnnotation(PathSensitive)?.value() ?: PathSensitivity.RELATIVE
    }

    private static class InputProperty {
        final Object value
        final AnnotatedElement element

        InputProperty(Object value, AnnotatedElement element) {
            this.value = value
            this.element = element
        }
    }

    /** A stable representation of a value, also for types without a meaningful {@code toString()}. */
    private static String fingerprint(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean ||
            value instanceof Character || value instanceof Enum || value instanceof File) {
            return String.valueOf(value)
        }
        if (value instanceof Collection || value.getClass().isArray()) {
            return '[' + value.collect { fingerprint(it) }.join(', ') + ']'
        }
        if (value instanceof Map) {
            return '[' + value.collect { k, v -> "${fingerprint(k)}: ${fingerprint(v)}" }.join(', ') + ']'
        }
        if (value.getClass().getMethod('toString').declaringClass != Object) {
            return value.toString()
        }
        if (value instanceof Serializable) {
            try {
                def bytes = new ByteArrayOutputStream()
                new ObjectOutputStream(bytes).withCloseable { it.writeObject(value) }
                return value.getClass().name + '@' + MessageDigest.getInstance('SHA-256').digest(bytes.toByteArray()).encodeHex()
            } catch (Exception ignored) {
                // not actually serializable
            }
        }
        return value.getClass().name
    }

    private static boolean isGetter(Method method) {
        method.parameterCount == 0 && !Modifier.isStatic(method.modifiers) &&
            ((method.name.startsWith('get') && method.name.length() > 3) || (method.name.startsWith('is') && method.name.length() > 2))
    }

    private static String propertyName(Method getter) {
        String name = getter.name.startsWith('get') ? getter.name.substring(3) : getter.name.substring(2)
        name.substring(0, 1).toLowerCase() + name.substring(1)
    }
}
