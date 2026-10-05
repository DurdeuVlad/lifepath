/**
 * Vanilla gameplay producers: translate raw Minecraft hooks (block breaks,
 * placements) into normalized {@code ActivityEvent}s. This is the only layer
 * that may know about vanilla gameplay types — everything downstream sees
 * domain events only.
 */
package com.dwurdy.lifepath.producer;
