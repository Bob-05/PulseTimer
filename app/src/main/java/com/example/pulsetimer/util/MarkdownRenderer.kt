package com.pulsetimer.util

/**
 * Минимальный рендерер Markdown → HTML.
 *
 * Заточен под документы проекта (PRIVACY_POLICY.md, TERMS_OF_USE.md):
 * заголовки, абзацы, маркированные списки, hr, таблицы, bold, italic,
 * inline-code, ссылки, fenced code blocks.
 *
 * Не является полноценным CommonMark-парсером. Если понадобится поддержка
 * вложенных списков, сносок, definition lists и т.п. — проще подключить
 * `org.commonmark:commonmark` (см. README проекта).
 *
 * Результат передаётся в WebView без JavaScript, что позволяет обойтись
 * без INTERNET-разрешения и без сетевой активности.
 */
object MarkdownRenderer {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val HR = Regex("^-{3,}\\s*$")
    private val INLINE_CODE = Regex("`([^`]+?)`")
    private val INLINE_BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val INLINE_ITALIC = Regex("(?<![*])\\*([^*\\n]+?)\\*(?![*])")
    private val INLINE_LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

    /**
     * Префикс маркированного списка: "- " или "* " (с любым количеством
     * ведущих пробелов). ВАЖНО: без `.*$` в конце — иначе replaceFirst
     * сотрёт содержимое строки целиком.
     */
    private val BULLET_PREFIX = Regex("^\\s*[-*]\\s+")

    fun toHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val out = StringBuilder(lines.size * 4)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            // --- Fenced code block ``` ---
            if (trimmed.startsWith("```")) {
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // closing fence
                out.append("<pre><code>")
                    .append(escapeHtml(code.toString().trimEnd('\n')))
                    .append("</code></pre>\n")
                continue
            }

            // --- Heading ---
            val h = HEADING.find(line)
            if (h != null) {
                val level = h.groupValues[1].length
                out.append("<h").append(level).append('>')
                    .append(inline(h.groupValues[2]))
                    .append("</h").append(level).append(">\n")
                i++
                continue
            }

            // --- Horizontal rule ---
            if (HR.matches(line)) {
                out.append("<hr>\n")
                i++
                continue
            }

            // --- Table: header row starts with '|', next line is separator ---
            if (trimmed.startsWith("|") &&
                i + 1 < lines.size &&
                isTableSeparator(lines[i + 1])
            ) {
                val tableLines = mutableListOf(line, lines[i + 1])
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    tableLines.add(lines[i])
                    i++
                }
                out.append(renderTable(tableLines))
                continue
            }

            // --- Bullet list ---
            if (BULLET_PREFIX.containsMatchIn(line)) {
                out.append("<ul>")
                while (i < lines.size && BULLET_PREFIX.containsMatchIn(lines[i])) {
                    // replaceFirst с префиксом-регексом удаляет ТОЛЬКО "- "
                    // (или "* "), а содержимое строки остаётся.
                    val content = lines[i].replaceFirst(BULLET_PREFIX, "")
                    out.append("<li>").append(inline(content)).append("</li>")
                    i++
                }
                out.append("</ul>\n")
                continue
            }

            // --- Blank line ---
            if (line.isBlank()) {
                i++
                continue
            }

            // --- Paragraph ---
            val para = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                val curTrim = cur.trimStart()
                if (cur.isBlank()) break
                if (HEADING.containsMatchIn(cur)) break
                if (HR.matches(cur)) break
                if (BULLET_PREFIX.containsMatchIn(cur)) break
                if (curTrim.startsWith("```")) break
                if (curTrim.startsWith("|") &&
                    i + 1 < lines.size &&
                    isTableSeparator(lines[i + 1])
                ) break
                para.add(cur)
                i++
            }
            if (para.isNotEmpty()) {
                out.append("<p>")
                    .append(para.joinToString("<br>") { inline(it) })
                    .append("</p>\n")
            } else {
                // Safety net от зацикливания.
                i++
            }
        }
        return out.toString()
    }

    /**
     * Разделитель таблицы — строка, состоящая только из `|`, `-`, `:` и пробелов,
     * содержащая хотя бы один `-`.
     *
     * Примеры, которые должны матчиться:
     *   |---|---|
     *   |---|
     *   | :--- | ---: |
     *   --- | ---
     *
     * Не матчится (это HR или что-то иное):
     *   ---
     *   ## Заголовок
     */
    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains('-')) return false
        return trimmed.all { it == '|' || it == '-' || it == ':' || it.isWhitespace() }
    }

    private fun renderTable(lines: List<String>): String {
        val sb = StringBuilder("<table>")
        lines.forEachIndexed { idx, raw ->
            if (idx == 1) return@forEachIndexed // separator row — пропускаем
            val cells = splitRow(raw)
            val tag = if (idx == 0) "th" else "td"
            sb.append("<tr>")
            cells.forEach { cell ->
                sb.append('<').append(tag).append('>')
                    .append(inline(cell))
                    .append("</").append(tag).append('>')
            }
            sb.append("</tr>")
        }
        sb.append("</table>\n")
        return sb.toString()
    }

    private fun splitRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split('|').map { it.trim() }
    }

    private fun inline(text: String): String {
        var s = escapeHtml(text)

        // 1. Inline code — вынимаем и защищаем плейсхолдерами, чтобы
        //    последующие регексы (bold/italic) не тронули его содержимое.
        val codeSpans = mutableListOf<String>()
        s = INLINE_CODE.replace(s) { m ->
            val idx = codeSpans.size
            codeSpans.add("<code>${m.groupValues[1]}</code>")
            "\u0000CODE$idx\u0000"
        }

        // 2. Bold **x** — обязательно до italic.
        s = INLINE_BOLD.replace(s, "<strong>$1</strong>")

        // 3. Italic *x* — не трогаем уже сконвертированные strong-маркеры.
        s = INLINE_ITALIC.replace(s, "<em>$1</em>")

        // 4. Links [text](url). Кавычка в URL ломала бы href — экранируем.
        s = INLINE_LINK.replace(s) { m ->
            val label = m.groupValues[1]
            val url = m.groupValues[2].replace("\"", "&quot;")
            "<a href=\"$url\">$label</a>"
        }

        // 5. Возвращаем code-спаны на место.
        codeSpans.forEachIndexed { idx, html ->
            s = s.replace("\u0000CODE$idx\u0000", html)
        }

        return s
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}