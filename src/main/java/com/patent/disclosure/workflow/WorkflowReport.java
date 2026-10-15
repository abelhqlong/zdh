package com.patent.disclosure.workflow;

import java.util.List;

public record WorkflowReport(
        String name,
        boolean success,
        boolean interrupted,
        List<StepResult> steps,
        long totalMs
) {}
