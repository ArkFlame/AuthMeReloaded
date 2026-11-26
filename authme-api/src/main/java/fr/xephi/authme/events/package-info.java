/**
 * Public event API of AuthMeRemastered.
 *
 * <p>All events in this package are supported API: plugins may listen for them through Bukkit's event bus and
 * may rely on the class names, constructors, getters and setters declared here. These signatures are kept
 * source- and binary-compatible across minor releases; new events and accessors may be added, while removals
 * and renames are reserved for major releases.
 *
 * <p>Events are fired synchronously unless their constructor documents an asynchronous flag. Listeners should
 * not depend on the order in which plugins handle an event and must avoid main-thread-only Bukkit calls while
 * handling an asynchronous event.
 *
 * <p>The abstract base classes hold state shared by the concrete events. Listeners may use them as a common
 * type, but their fields are implementation details: read and write event state through the accessors only.
 */
package fr.xephi.authme.events;
