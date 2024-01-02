package org.compiler;

/**
 * Represents a statement.
 * @param firstLabel label of the code block starting
 *                   with this statement (might be null)
 * @param code code of the statement
 */
public record Statement(String firstLabel, String code) {
}
