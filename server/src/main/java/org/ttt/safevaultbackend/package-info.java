/**
 * SafeVault backend root package.
 *
 * <p>Boundary rule:
 * controller -> service -> repository/entity
 *
 * <p>Security package is shared infrastructure for authentication and request
 * filtering and should be consumed via framework wiring instead of direct
 * business coupling.
 */
package org.ttt.safevaultbackend;
