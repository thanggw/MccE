package com.mcce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Judge0SubmissionResponse {

	private String stdout;
	private String stderr;

	@JsonProperty("compile_output")
	private String compileOutput;

	private Judge0Status status;

	public String getStdout() {
		return stdout;
	}

	public void setStdout(String stdout) {
		this.stdout = stdout;
	}

	public String getStderr() {
		return stderr;
	}

	public void setStderr(String stderr) {
		this.stderr = stderr;
	}

	public String getCompileOutput() {
		return compileOutput;
	}

	public void setCompileOutput(String compileOutput) {
		this.compileOutput = compileOutput;
	}

	public Judge0Status getStatus() {
		return status;
	}

	public void setStatus(Judge0Status status) {
		this.status = status;
	}

	public static class Judge0Status {
		private Integer id;
		private String description;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}
	}
}
