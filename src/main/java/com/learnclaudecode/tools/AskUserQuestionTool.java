package com.learnclaudecode.tools;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 向用户提问工具 — Claude Code AskUserQuestion 等价实现。
 * 模型通过 JSON 字符串传入问题列表，终端交互收集用户回答。
 *
 * @author BEAM
 */
@Slf4j
public class AskUserQuestionTool {

    private final Scanner scanner;

    public AskUserQuestionTool(Scanner scanner) {
        this.scanner = scanner;
    }

    @AgentTool(name = "AskUserQuestion", description = """
            Use this tool when you need to ask the user questions during execution. This allows you to:
            1. Gather user preferences or requirements
            2. Clarify ambiguous instructions
            3. Get decisions on implementation choices as you work
            4. Offer choices to the user about what direction to take.

            Usage notes:
            - Users will always be able to select "Other" to provide custom text input
            - Use multiSelect: true to allow multiple answers to be selected for a question
            - If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label

            questions parameter: JSON array of question objects. Each question has:
            - question: The complete question to ask (string, required)
            - header: Short label for the question, max 12 chars (string, required)
            - options: Array of {label, description} objects, 2-4 options (required)
            - multiSelect: Whether multiple selections allowed (boolean, optional, default false)""")
    public String askUserQuestion(
            @AgentToolParam(description = "JSON string: array of 1-4 question objects. Each object: {question, header, options: [{label, description}], multiSelect?}") String questions) {

        if (questions == null || questions.isBlank()) {
            return "Error: questions must not be empty";
        }

        try {
            List<Map<String, Object>> questionList = JsonUtils.fromJson(questions,
                    new TypeReference<List<Map<String, Object>>>() {});

            if (questionList.isEmpty() || questionList.size() > 4) {
                return "Error: must provide 1-4 questions, got " + questionList.size();
            }

            StringBuilder result = new StringBuilder();
            for (int qi = 0; qi < questionList.size(); qi++) {
                Map<String, Object> q = questionList.get(qi);
                String questionText = String.valueOf(q.getOrDefault("question", "Question " + (qi + 1)));
                String header = String.valueOf(q.getOrDefault("header", "Q" + (qi + 1)));

                System.out.println();
                System.out.println("--- " + header + " ---");
                System.out.println(questionText);
                System.out.println();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> options = (List<Map<String, Object>>) q.get("options");
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        Map<String, Object> opt = options.get(i);
                        String label = String.valueOf(opt.getOrDefault("label", "Option " + (i + 1)));
                        String desc = String.valueOf(opt.getOrDefault("description", ""));
                        System.out.println("  " + (i + 1) + ". " + label + " - " + desc);
                    }
                }
                System.out.println("  0. Other (custom input)");
                System.out.println();
                System.out.print("Choose [0-" + (options != null ? options.size() : 0) + "]: ");

                String input = scanner.nextLine().trim();
                if ("0".equals(input)) {
                    System.out.print("Custom input: ");
                    input = scanner.nextLine().trim();
                } else if (options != null) {
                    try {
                        int idx = Integer.parseInt(input) - 1;
                        if (idx >= 0 && idx < options.size()) {
                            input = String.valueOf(options.get(idx).getOrDefault("label", input));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                result.append(questionText).append(" -> ").append(input).append("\n");
            }

            return "User answered:\n" + result.toString().trim();

        } catch (Exception e) {
            log.error("AskUserQuestion failed: {}", e.getMessage());
            return "Error: Failed to parse questions JSON: " + e.getMessage();
        }
    }
}
