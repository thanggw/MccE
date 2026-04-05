package com.mcce.service;

import com.mcce.dto.ExecutionResult;
import java.io.IOException;

public interface ExecutionService {
	ExecutionResult execute(String code, String language) throws IOException, InterruptedException;
}
