package com.tik;


import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        String apiKey = "";
        String model = "gpt-4o-mini";
        List<CompletionBuilder.Message> messages = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;

            System.out.println("Enter lines of text (type 'exit' to quit):");

            while (true) {
                System.out.print("> ");
                line = reader.readLine();

                if (line == null || "exit".equalsIgnoreCase(line.trim())) {
                    System.out.println("Goodbye!");
                    break;
                }

                messages.add(CompletionBuilder.Message.builder()
                        .role("user")
                        .content(List.of(CompletionBuilder.ContentBuilder.build(Type.TEXT, line)))
                        .build()
                );
                String respMessage= CompletionBuilder.builder()
                        .apiKey(apiKey)
                        .body(CompletionBuilder.Body.builder()
                                .model(model)
                                .stream(true)
                                .messages(messages)
                                .build()
                        )
                        .build()
                        .execute();
                messages.add(CompletionBuilder.Message.builder()
                        .role("assistant")
                        .content(respMessage)
                        .build()
                );
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        }


    }
}