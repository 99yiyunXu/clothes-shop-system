package com.example.demo.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

	// ログイン可能なユーザー一覧
	private final List<User> users = List.of(
			new User("admin", "123456"),
			new User("luna", "131400"),
			new User("lihua", "1234"),
			new User("lisi", "5678"),
			new User("test", "test123")
	);

	// ログイン画面を表示する
	@GetMapping("/")
	public String index() {
		return "index";
	}

	// ログイン処理
	@PostMapping("/login")
	@ResponseBody
	public String login(
			@RequestParam String userName,
			@RequestParam String password,
			HttpSession session) {

		// 登録されているユーザーを確認する
		for (User user : users) {

			// ユーザー名とパスワードが一致した場合
			if (user.getUserName().equals(userName)
					&& user.getPassword().equals(password)) {
				
				session.setAttribute("loginUser", userName);

				return "ok";
			}
		}

		
		// ログインに失敗した場合
		return "NG";
		
	}

	// ログアウト処理
	@GetMapping("/logout")
	public String logout(HttpSession session) {

		// セッションを削除する
		session.invalidate();

		return "redirect:/";
	}
	
	// ユーザー情報を管理するクラス
	static class User {

		private String userName;
		private String password;

		public User(String userName, String password) {
			this.userName = userName;
			this.password = password;
		}

		public String getUserName() {
			return userName;
		}

		public String getPassword() {
			return password;
		}
	}
}