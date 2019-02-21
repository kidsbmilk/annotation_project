/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.codegen

object CodeFormatter {

  def format(code: String, maxLines: Int = -1): String = {
    val strippedCode = stripExtraNewLines(code)
    val formatter = new CodeFormatter
    val lines = strippedCode.split("\n")
    val needToTruncate = maxLines >= 0 && lines.length > maxLines
    val filteredLines = if (needToTruncate) lines.take(maxLines) else lines
    filteredLines.foreach(formatter.addLine)
    if (needToTruncate) {
      formatter.addLine(s"[truncated to $maxLines lines (total lines is ${lines.length})]")
    }
    formatter.result()
  }

  def stripExtraNewLines(input: String): String = {
    val code = new StringBuilder
    var lastLine: String = "dummy"
    input.split('\n').foreach { l =>
      val line = l.trim()
      val skip = line == "" && (lastLine == "" || lastLine.endsWith("{") || lastLine.endsWith("*/"))
      if (!skip) {
        code.append(line)
        code.append("\n")
      }
      lastLine = line
    }
    code.result()
  }
}

private class CodeFormatter {
  private val code = new StringBuilder
  private val indentSize = 2

  // Tracks the level of indentation in the current line.
  private var indentLevel = 0
  private var indentString = ""
  private var currentLine = 1

  // Tracks the level of indentation in multi-line comment blocks.
  private var inCommentBlock = false
  private var indentLevelOutsideCommentBlock = indentLevel

  private def addLine(line: String): Unit = {

    // We currently infer the level of indentation of a given line based on a simple heuristic that
    // examines the number of parenthesis and braces in that line. This isn't the most robust
    // implementation but works for all code that we generate.
    val indentChange = line.count(c => "({".indexOf(c) >= 0) - line.count(c => ")}".indexOf(c) >= 0)
    var newIndentLevel = math.max(0, indentLevel + indentChange)

    // Please note that while we try to format the comment blocks in exactly the same way as the
    // rest of the code, once the block ends, we reset the next line's indentation level to what it
    // was immediately before entering the comment block.
    if (!inCommentBlock) {
      if (line.startsWith("/*")) {
        // Handle multi-line comments
        inCommentBlock = true
        indentLevelOutsideCommentBlock = indentLevel
      } else if (line.startsWith("//")) {
        // Handle single line comments
        newIndentLevel = indentLevel
      }
    }
    if (inCommentBlock) {
      if (line.endsWith("*/")) {
        inCommentBlock = false
        newIndentLevel = indentLevelOutsideCommentBlock
      }
    }

    // Lines starting with '}' should be de-indented even if they contain '{' after;
    // in addition, lines ending with ':' are typically labels
    val thisLineIndent = if (line.startsWith("}") || line.startsWith(")") || line.endsWith(":")) {
      " " * (indentSize * (indentLevel - 1))
    } else {
      indentString
    }
    code.append(f"/* ${currentLine}%03d */")
    if (line.trim().length > 0) {
      code.append(" ") // add a space after the line number comment.
      code.append(thisLineIndent)
      if (inCommentBlock && line.startsWith("*") || line.startsWith("*/")) code.append(" ")
      code.append(line)
    }
    code.append("\n")
    indentLevel = newIndentLevel
    indentString = " " * (indentSize * newIndentLevel)
    currentLine += 1
  }

  private def result(): String = code.result()
}
