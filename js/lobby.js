"use strict";

const roomButtons = document.querySelectorAll(".enter-button");
const createRoomButton = document.getElementById("create-room-button");
const joinRoomForm = document.getElementById("join-room-form");
const roomIdInput = document.getElementById("room-id");
const roomIdError = document.getElementById("room-id-error");

/**
 * 選択したテーマのパブリック部屋へ移動する仮処理です。
 * API導入後は、入室可能か確認してから移動する処理に置き換えます。
 */
function enterRoom(theme) {
  window.location.href = `room.html?theme=${encodeURIComponent(theme)}`;
}

/** プライベート部屋作成画面へ移動します。 */
function createPrivateRoom() {
  window.location.href = "create-room.html";
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
  window.location.href = `room.html?roomId=${encodeURIComponent(roomId)}`;
}

// HTMLには処理を直接書かず、ここで各ボタンにイベントを設定します。
roomButtons.forEach(function (button) {
  button.addEventListener("click", function () {
    enterRoom(button.dataset.theme);
  });
});

createRoomButton.addEventListener("click", createPrivateRoom);

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
