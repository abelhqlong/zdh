package com.patent.disclosure.workflow;

import java.util.List;

public record WorkflowDefinition(
        String name,
        String description,
        List<WorkflowStep> steps
) {}
