package com.eyeem.potato.example.model;

public class Tweet {
	
	private String id;
	private String text;
	private User author;
	
	private User retweetedUser;
	
	public Tweet() {
		id = null;
		text = null;
		author = null;
		retweetedUser = null;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public User getUser() {
		return author;
	}
	public void setUser(User user) {
		this.author = user;
	}
	
	public User getRetweetedUser() {
		return retweetedUser;
	}

	public void setRetweetedUser(User retweetedUser) {
		this.retweetedUser = retweetedUser;
	}
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
}
