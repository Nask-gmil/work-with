"use strict";

const registerForm = document.getElementById("register-form");
const registerUsername = document.getElementById("register-username");
const registerPassword = document.getElementById("register-password");
const confirmPassword = document.getElementById("confirm-password");
const turnstileError = document.getElementById("turnstile-error");
let turnstileToken = "";
let turnstileWidgetId = null;

const registerFields = [
  {
    input: registerUsername,
    error: document.getElementById("username-error")
  },
  {
    input: registerPassword,
    error: document.getElementById("password-error")
  },
  {
    input: confirmPassword,
    error: document.getElementById("confirm-password-error")
  }
];

/** 指定した入力欄の下にエラーを表示します。 */
function showRegisterError(field, message) {
  field.error.textContent = message;
  field.input.setAttribute("aria-invalid", "true");
}

/** 以前に表示したすべてのエラーを消します。 */
function clearRegisterErrors() {
  registerFields.forEach(function (field) {
    field.error.textContent = "";
    field.input.removeAttribute("aria-invalid");
  });
  turnstileError.textContent = "";
}

function resetTurnstile() {
  turnstileToken = "";
  if (turnstileWidgetId !== null && window.turnstile) {
    window.turnstile.reset(turnstileWidgetId);
  }
}

async function initializeTurnstile() {
  try {
    const response = await fetch("/api/public-config");
    const config = await response.json();
    if (!response.ok || !config.turnstileSiteKey) {
      throw new Error("Turnstile sitekey is unavailable");
    }

    window.onTurnstileScriptLoaded = function () {
      turnstileWidgetId = window.turnstile.render("#turnstile-widget", {
        sitekey: config.turnstileSiteKey,
        size: "flexible",
        callback: function (token) {
          turnstileToken = token;
          turnstileError.textContent = "";
        },
        "expired-callback": function () {
          turnstileToken = "";
          turnstileError.textContent = "確認の有効期限が切れました。もう一度確認してください。";
        },
        "error-callback": function () {
          turnstileToken = "";
          turnstileError.textContent = "確認を読み込めませんでした。時間を置いて再度お試しください。";
        }
      });
    };

    const script = document.createElement("script");
    script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?onload=onTurnstileScriptLoaded&render=explicit";
    script.async = true;
    script.defer = true;
    document.head.appendChild(script);
  } catch (error) {
    turnstileError.textContent = "確認機能を読み込めませんでした。時間を置いて再度お試しください。";
  }
}

/**
 * フロント側だけで入力内容を確認する仮の登録処理です。
 * Spring Boot導入後は、成功時に登録APIを呼ぶ処理へ置き換えます。
 */
async function registerUser() {
  const username = registerUsername.value.trim();
  const password = registerPassword.value;
  const passwordConfirmation = confirmPassword.value;
  const usernamePattern = /^[A-Za-z0-9_]+$/;

  clearRegisterErrors();

  if (username === "") {
    showRegisterError(registerFields[0], "ユーザーネームを入力してください");
    registerUsername.focus();
    return;
  }

  if (!usernamePattern.test(username)) {
    showRegisterError(
      registerFields[0],
      "ユーザーネームは半角英数字とアンダースコアで入力してください"
    );
    registerUsername.focus();
    return;
  }

  if (password.length < 8) {
    showRegisterError(registerFields[1], "パスワードは8文字以上で入力してください");
    registerPassword.focus();
    return;
  }

  if (password !== passwordConfirmation) {
    showRegisterError(registerFields[2], "パスワードが一致しません");
    confirmPassword.focus();
    return;
  }

  if (!turnstileToken) {
    turnstileError.textContent = "確認を完了してください。";
    return;
  }

  const registerButton = registerForm.querySelector("button[type='submit']");
  registerButton.disabled = true;

  try {
    const response = await fetch("/api/users/register", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        username: username,
        password: password,
        turnstileToken: turnstileToken
      })
    });
    const responseBody = await response.json();

    if (!response.ok) {
      const message = responseBody.message || "ユーザー登録に失敗しました";
      if (response.status === 429 || response.status === 503 || message.includes("確認")) {
        turnstileError.textContent = message;
      } else if (message.includes("パスワード")) {
        showRegisterError(registerFields[1], message);
        registerPassword.focus();
      } else {
        showRegisterError(registerFields[0], message);
        registerUsername.focus();
      }
      resetTurnstile();
      return;
    }

    // 登録成功後は既存どおりログイン画面へ戻り、入力したusernameを引き継ぎます。
    document.getElementById("username").value = responseBody.username;
    registerForm.reset();
    resetTurnstile();
    showView("login");
    document.getElementById("password").focus();
  } catch (error) {
    showRegisterError(
      registerFields[0],
      "サーバーに接続できません。Spring Bootが起動しているか確認してください"
    );
  } finally {
    registerButton.disabled = false;
  }
}

registerForm.addEventListener("submit", function (event) {
  event.preventDefault();
  registerUser();
});

initializeTurnstile();

// 入力を修正し始めたら、その欄のエラー表示を消します。
registerFields.forEach(function (field) {
  field.input.addEventListener("input", function () {
    field.error.textContent = "";
    field.input.removeAttribute("aria-invalid");
  });
});
