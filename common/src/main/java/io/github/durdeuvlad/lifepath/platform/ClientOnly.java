package io.github.durdeuvlad.lifepath.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks code that must only run on the physical client (M13-3 replacement
 * for {@code @Environment(EnvType.CLIENT)} — common can't import the loader's
 * annotation). Documentation + review signal only; the split sourceset layout
 * is what actually keeps these classes off the dedicated-server classpath.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface ClientOnly {
}
