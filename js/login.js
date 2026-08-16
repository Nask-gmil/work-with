"use strict";

const loginForm = document.getElementById("login-form");
const loginUsername = document.getElementById("username");
const loginPassword = document.getElementById("password");
const loginButton = loginForm.querySelector("button[type='submit']");

// HTML/CSSを変更せず、既存のエラー用デザインをログイン画面でも利用します。
const loginError = document.createElement("p");
loginError.className = "register-error";
loginError.setAttribute("aria-live", "polite");
loginForm.insertBefore(loginError, loginButton);

function showLoginError(message) {
  loginError.textContent = message;
  loginUsername.setAttribute("aria-invalid", "true");
  loginPassword.setAttribute("aria-invalid", "true");
}

function clearLoginError() {
  loginError.textContent = "";
  loginUsername.removeAttribute("aria-invalid");
  loginPassword.removeAttribute("aria-invalid");
}

/** 入力情報をログインAPIへ送り、成功した場合だけロビーを表示します。 */
async function login() {
  const username = loginUsername.value.trim().normalize("NFC");
  const password = loginPassword.value;

  clearLoginError();
  loginButton.disabled = true;

  try {
    const response = await fetch("/api/users/login", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ username: username, password: password })
    });
    const responseBody = await response.json();

    if (!response.ok) {
      showLoginError(responseBody.message || "ログインに失敗しました");
      return;
    }

    // 認証状態はサーバーのHttpSessionが管理し、画面用情報だけをメモリへ保持します。
    setAuthenticatedUser(responseBody);
    loginPassword.value = "";
    showView("lobby");
  } catch (error) {
    showLoginError("サーバーに接続できません。Spring Bootが起動しているか確認してください");
  } finally {
    loginButton.disabled = false;
  }
}

loginForm.addEventListener("submit", function (event) {
  event.preventDefault();
  login();
});

[loginUsername, loginPassword].forEach(function (input) {
  input.addEventListener("input", clearLoginError);
});
