package org.example;

import static org.example.FactExtractor.getDependencies;

public class PromptBuilder {

    public static String buildPrompt(String controllerCode) {
        return "Generate at least 4 JUnit-5 tests using MockMvc for the next Spring controller. " +
                "Respond ONLY with a single code block in the format ```java ... ```, without any explanations or comments.\n\n```java\n"
                + controllerCode + "\n```";
    }

    public static String buildPromptWithContext(String controllerCode, String className) {
        String projectKnowledge = null;

        try {
            projectKnowledge = getDependencies(className);

            if(!projectKnowledge.isEmpty()) {
                System.out.println("НАЙДЕНЫ РЕЛЕВАНТНЫЕ ДАННЫЕ:");
            }
            System.out.println(projectKnowledge);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String prompt = "Generate at least 4 JUnit-5 tests using MockMvc for the following Spring controller, taking into account its dependencies and imported classes.\n";
        prompt += "Controller code:\n```java\n" + controllerCode + "\n```\n";

        if (projectKnowledge != null && !projectKnowledge.isEmpty()) {
            prompt += "\nDependency code:\n\n" + projectKnowledge + "\n";
        }

        prompt += "\nRespond ONLY with the test code inside a single ```java code block``` and nothing else.";

        return prompt;
    }
}
