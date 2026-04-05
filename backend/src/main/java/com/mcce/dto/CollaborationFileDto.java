package com.mcce.dto;

public class CollaborationFileDto {
	private String id;
	private String name;
	private String content;
	private String language;
	private int version;

	public CollaborationFileDto() {}

	public CollaborationFileDto(String id, String name, String content, String language, int version) {
		this.id = id;
		this.name = name;
		this.content = content;
		this.language = language;
		this.version = version;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}
}
