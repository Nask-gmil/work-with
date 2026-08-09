"use strict";

const loginForm = document.getElementById("login-form");

/**
 * 現在はロビー画面を表示するだけの仮ログイン処理です。
 * Spring Boot導入後は、この関数内をAPI通信と認証処理に置き換えます。
 */
function login() {
  // バックエンド導入前の仮処理として、入力された名前をセッションに保存します。
  const username = document.getElementById("username").value.trim();
  sessionStorage.setItem("username", username);
  showView("lobby");
}

// HTML標準の入力チェックを通過したときだけ、仮ログイン処理を実行します。
loginForm.addEventListener("submit", function (event) {
  event.preventDefault();
  login();
});
