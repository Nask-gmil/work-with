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

let selectedAvatar = null;

/** セッションから現在のユーザー名を取得します。 */
function getCurrentUsername() {
  return sessionStorage.getItem("username") || "yamada_taro";
}

/** 現在のユーザーが保存済みのアバターを取得します。 */
function getSelectedAvatar() {
  const username = getCurrentUsername();
  return localStorage.getItem(`avatarType:${username}`);
}

/** 選択したアバターを現在のユーザー専用のキーで保存します。 */
function saveSelectedAvatar(avatarType) {
  const username = getCurrentUsername();
  localStorage.setItem(`avatarType:${username}`, avatarType);
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
function initializeLobby() {
  currentUser.textContent = `${getCurrentUsername()}さん`;

  if (!getSelectedAvatar()) {
    showAvatarModal();
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

confirmAvatarButton.addEventListener("click", function () {
  if (!selectedAvatar) {
    avatarError.textContent = "アバターを選択してください";
    return;
  }

  saveSelectedAvatar(selectedAvatar);
  closeAvatarModal();
});

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
