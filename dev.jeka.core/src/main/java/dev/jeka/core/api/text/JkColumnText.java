/*
 * Copyright 2014-2024  the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.jeka.core.api.text;

import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsString;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for formatting text into columns.
 */
public final class JkColumnText {

    private String separator = "  ";

    private String marginLeft = "";

    private int numColumns;

    private final List<Integer> minColumnSizes = new LinkedList<>();

    private final List<Integer> maxColumnSizes = new LinkedList<>();

    private final List<String[]> rows = new LinkedList<>();

    private JkColumnText() {
    }

    /**
     * Creates a new instance with a single column, specified by the minimum and maximum sizes.
     */
    public static JkColumnText ofSingle(int minSize, int maxSize) {
        JkColumnText result = new JkColumnText();
        result.addColumn(minSize, maxSize);
        return result;
    }

    /**
     * Adds a column to this object with the specified minimum and maximum sizes.
     */
    public JkColumnText addColumn(int minSize, int maxSize) {
        JkUtilsAssert.argument(minSize <= maxSize, "Max size %s can't be lesser than min size %s", maxSize, minSize);
        numColumns ++;
        minColumnSizes.add(minSize);
        maxColumnSizes.add(maxSize);
        return this;
    }

    /**
     * Adds a column to this object with the specified size.
     */
    public JkColumnText addColumn(int size) {
        return addColumn(size, size);
    }

    /**
     * Sets the separator to separate columns.
     */
    public JkColumnText setSeparator(String separator) {
        this.separator = separator;
        return this;
    }

    /**
     * Sets the left margin to lead the whole result
     */
    public JkColumnText setMarginLeft(String marginLeft) {
        this.marginLeft = marginLeft;
        return this;
    }

    /**
     * Adds a row by providing text for each column.
     */
    public JkColumnText add(String ... row) {
        rows.add(row);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Looking for each row
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            // Inside each row we may need to have several nested lines
            int nestedLineCount = lineCountForRow(rowIndex);
            for (int nestedLineIndex = 0; nestedLineIndex < nestedLineCount; nestedLineIndex++) {

                sb.append(marginLeft);

                // Compute the content of each nested line
                String[] row = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex< numColumns; columnIndex++) {
                    int columnSize = computeColumnSize(columnIndex);
                    String cellText = Optional.ofNullable(row[columnIndex]).orElse("");
                    String ellipse = JkUtilsString.ellipse(cellText, columnSize);
                    String padded = JkUtilsString.padEnd(ellipse,  columnSize, ' ');
                    sb.append(padded).append(separator);
                }
                sb.append("\n");
            }
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() -1);
        }
        return sb.toString();
    }

    private int lineCountForRow(int rowIndex) {
        String[] row = this.rows.get(rowIndex);
        int result = 1;
        for (int i =0; i < numColumns; i++) {
            int columnSize = computeColumnSize(i);
            String originalText = row[i];
            String wrappedText = JkUtilsString.wrapStringCharacterWise(originalText, columnSize);
            int lineCount = wrappedText.split("\n").length;
            result = Math.max(lineCount, result);
        }
        return result;
    }

    private int computeColumnSize(int columnIndex) {
        int minSize = minColumnSizes.get(columnIndex);
        int maxSize = maxColumnSizes.get(columnIndex);
        int result = maxTextSize(columnIndex);
        if (result > maxSize) {
            result = maxSize;
        }
        if (result < minSize) {
            result = minSize;
        }
        return result;
    }

    private int maxTextSize(int columnIndex) {
        return rows.stream()
                .map(row -> row[columnIndex])
                .map(row -> Optional.ofNullable(row).orElse("").length())
                .max(Comparator.naturalOrder()).orElse(0);
    }


}