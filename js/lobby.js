"use strict";

const roomButtons = document.querySelectorAll(".enter-button");
const createRoomButton = document.getElementById("create-room-button");
const joinRoomForm = document.getElementById("join-room-form");
const roomIdInput = document.getElementById("room-id");
const roomIdError = document.getElementById("room-id-error");
const currentUser = document.querySelector(".current-user");
const avatarModalOverlay = document.getElementById("avatar-modal-overlay");
const avatarOptions = document.querySelectorAll(".avatar-option");
const confirmAvatarButton = document.getElementById("confirm-avatar-button");
const avatarError = document.getElementById("avatar-error");
const logoutButton = document.getElementById("logout-button");

let selectedAvatar = null;

const legacyAvatarTypes = {
  maleA: "male_a",
  maleB: "male_b",
  femaleA: "female_a",
  femaleB: "female_b"
};

/** 旧UIの保存値を、v6で確定したDB向けの形式へ変換します。 */
function normalizeAvatarType(avatarType) {
  const normalizedType = legacyAvatarTypes[avatarType] || avatarType;
  return ["male_a", "male_b", "female_a", "female_b"].includes(normalizedType)
    ? normalizedType
    : null;
}

/** セッションから現在のユーザー名を取得します。 */
function getCurrentUsername() {
  return getAuthenticatedUser()?.username || "ゲスト";
}

/** 現在のユーザーが保存済みのアバターを取得します。 */
function getSelectedAvatar() {
  return normalizeAvatarType(getAuthenticatedUser()?.avatarType);
}

/** 選択したアバターをSpring Boot API経由でSQLiteへ保存します。 */
async function saveSelectedAvatar(avatarType) {
  const normalizedAvatar = normalizeAvatarType(avatarType);
  if (!normalizedAvatar) {
    throw new Error("アバターを選択してください");
  }

  const response = await fetch("/api/users/me/avatar", {
    method: "PATCH",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ avatarType: normalizedAvatar })
  });
  const responseBody = await response.json();

  if (response.status === 401) {
    clearAuthenticatedUser();
    closeAvatarModal();
    showView("login");
    return null;
  }
  if (!response.ok) {
    throw new Error(responseBody.message || "アバターを保存できませんでした");
  }

  setAuthenticatedUser(responseBody);
  return responseBody;
}

/** アバター選択モーダルを表示します。将来の変更画面からも呼び出せます。 */
function showAvatarModal() {
  selectedAvatar = getSelectedAvatar();
  avatarError.textContent = "";

  avatarOptions.forEach(function (option) {
    const isSelected = option.dataset.avatar === selectedAvatar;
    option.classList.toggle("is-selected", isSelected);
    option.setAttribute("aria-pressed", String(isSelected));
  });

  avatarModalOverlay.hidden = false;
  document.body.classList.add("has-open-modal");
  avatarOptions[0].focus();
}

/** 保存後にモーダルを閉じ、ロビーを操作できる状態へ戻します。 */
function closeAvatarModal() {
  avatarModalOverlay.hidden = true;
  document.body.classList.remove("has-open-modal");
}

/** ロビー表示時にユーザー名と初回アバター選択の要否を反映します。 */
async function initializeLobby() {
  try {
    const response = await fetch("/api/users/me", {
      credentials: "same-origin"
    });

    if (response.status === 401) {
      clearAuthenticatedUser();
      closeAvatarModal();
      showView("login");
      return;
    }
    if (!response.ok) {
      throw new Error("ユーザー情報を取得できませんでした");
    }

    const user = await response.json();
    setAuthenticatedUser(user);
    currentUser.textContent = `${user.username}さん`;

    if (!getSelectedAvatar()) {
      showAvatarModal();
    } else {
      closeAvatarModal();
    }
  } catch (error) {
    clearAuthenticatedUser();
    closeAvatarModal();
    showView("login");
  }
}

async function logout() {
  try {
    await fetch("/api/users/logout", {
      method: "POST",
      credentials: "same-origin"
    });
  } finally {
    clearAuthenticatedUser();
    closeAvatarModal();
    showView("login");
  }
}

/**
 * 選択したテーマのパブリック部屋へ移動する仮処理です。
 * API導入後は、入室可能か確認してから移動する処理に置き換えます。
 */
function enterRoom(theme) {
  const query = new URLSearchParams({ theme: theme }).toString();
  showView("room", true, query);
}

/** プライベート部屋作成画面へ移動します。 */
function openCreateRoomView() {
  showView("create-room");
}

/**
 * 入力された部屋IDを使って入室する仮処理です。
 * 現段階では部屋が実在するかどうかは確認しません。
 */
function joinPrivateRoom() {
  const roomId = roomIdInput.value.trim();

  if (roomId === "") {
    roomIdError.textContent = "部屋IDを入力してください";
    roomIdInput.setAttribute("aria-invalid", "true");
    roomIdInput.focus();
    return;
  }

  roomIdError.textContent = "";
  roomIdInput.removeAttribute("aria-invalid");
  const privateRoom = findPrivateRoomById(roomId);

  if (!privateRoom) {
    roomIdError.textContent = "その部屋IDは存在しません";
    roomIdInput.setAttribute("aria-invalid", "true");
    return;
  }

  enterPrivateRoom(privateRoom.roomId);
}

// HTMLには処理を直接書かず、ここで各ボタンにイベントを設定します。
roomButtons.forEach(function (button) {
  button.addEventListener("click", function () {
    enterRoom(button.dataset.theme);
  });
});

createRoomButton.addEventListener("click", openCreateRoomView);

joinRoomForm.addEventListener("submit", function (event) {
  event.preventDefault();
  joinPrivateRoom();
});

// 入力を始めたら、表示中のエラーを消します。
roomIdInput.addEventListener("input", function () {
  if (roomIdInput.value.trim() !== "") {
    roomIdError.textContent = "";
    roomIdInput.removeAttribute("aria-invalid");
  }
});

avatarOptions.forEach(function (option) {
  option.addEventListener("click", function () {
    selectedAvatar = option.dataset.avatar;
    avatarError.textContent = "";

    avatarOptions.forEach(function (otherOption) {
      const isSelected = otherOption === option;
      otherOption.classList.toggle("is-selected", isSelected);
      otherOption.setAttribute("aria-pressed", String(isSelected));
    });
  });
});

confirmAvatarButton.addEventListener("click", async function () {
  if (!selectedAvatar) {
    avatarError.textContent = "アバターを選択してください";
    return;
  }

  avatarError.textContent = "";
  confirmAvatarButton.disabled = true;

  try {
    const updatedUser = await saveSelectedAvatar(selectedAvatar);
    if (!updatedUser) {
      return;
    }
    document.dispatchEvent(new CustomEvent("avatarupdated"));
    closeAvatarModal();
  } catch (error) {
    avatarError.textContent = error.message;
  } finally {
    confirmAvatarButton.disabled = false;
  }
});

logoutButton.addEventListener("click", logout);

// SPAでロビーへ切り替わるたびに初回判定を行います。
document.addEventListener("viewchange", function (event) {
  if (event.detail.view === "lobby") {
    initializeLobby();
  }
});

// main.html#lobbyを直接開いた場合にも初期化します。
if (!document.getElementById("lobby-view").hidden) {
  initializeLobby();
}
