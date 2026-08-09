"use strict";

const createRoomForm = document.getElementById("create-room-form");
const privateRoomNameInput = document.getElementById("private-room-name");
const createdRoomResult = document.getElementById("created-room-result");
const createdRoomName = document.getElementById("created-room-name");
const createdRoomId = document.getElementById("created-room-id");
const copyRoomIdButton = document.getElementById("copy-room-id-button");
const copyStatus = document.getElementById("copy-status");
const enterCreatedRoomButton = document.getElementById("enter-created-room-button");

let latestCreatedRoom = null;

/** localStorageからプライベート部屋一覧を取得します。 */
function getPrivateRooms() {
  try {
    const privateRooms = JSON.parse(localStorage.getItem("privateRooms")) || [];
    return Array.isArray(privateRooms) ? privateRooms : [];
  } catch (error) {
    return [];
  }
}

/** プライベート部屋一覧をlocalStorageへ保存します。 */
function savePrivateRooms(privateRooms) {
  localStorage.setItem("privateRooms", JSON.stringify(privateRooms));
}

/** abc-1234形式のランダムな部屋IDを生成します。 */
function generateRoomId() {
  const letters = "abcdefghijklmnopqrstuvwxyz";
  let prefix = "";

  for (let index = 0; index < 3; index += 1) {
    prefix += letters[Math.floor(Math.random() * letters.length)];
  }

  const number = Math.floor(Math.random() * 10000).toString().padStart(4, "0");
  return `${prefix}-${number}`;
}

/** 保存済み一覧から指定した部屋IDを検索します。 */
function findPrivateRoomById(roomId) {
  const normalizedId = roomId.trim().toLowerCase();
  return getPrivateRooms().find(function (room) {
    return room.roomId === normalizedId;
  });
}

/** 入力内容から部屋を作成し、既存データへ追加します。 */
function createPrivateRoom() {
  const username = sessionStorage.getItem("username") || "ゲスト";
  const enteredName = privateRoomNameInput.value.trim();
  const theme = createRoomForm.elements.roomTheme.value;
  const privateRooms = getPrivateRooms();
  let roomId = generateRoomId();

  while (privateRooms.some(function (room) { return room.roomId === roomId; })) {
    roomId = generateRoomId();
  }

  const room = {
    roomId: roomId,
    roomName: enteredName || `${username}の部屋`,
    theme: theme,
    createdBy: username,
    currentUsers: 0
  };

  privateRooms.push(room);
  savePrivateRooms(privateRooms);
  return room;
}

/** 作成した部屋情報を成功カードへ表示します。 */
function showCreatedRoom(room) {
  latestCreatedRoom = room;
  createdRoomName.textContent = room.roomName;
  createdRoomId.textContent = room.roomId;
  copyStatus.textContent = "";
  copyRoomIdButton.textContent = "コピー";
  createdRoomResult.hidden = false;
  createdRoomResult.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

/** SPA内の仮部屋画面へ移動します。 */
function enterPrivateRoom(roomId) {
  const query = new URLSearchParams({ roomId: roomId }).toString();
  showView("room", true, query);
}

createRoomForm.addEventListener("submit", function (event) {
  event.preventDefault();
  showCreatedRoom(createPrivateRoom());
});

copyRoomIdButton.addEventListener("click", async function () {
  if (!latestCreatedRoom) return;

  try {
    await navigator.clipboard.writeText(latestCreatedRoom.roomId);
    copyRoomIdButton.textContent = "コピー済み";
    copyStatus.textContent = "コピーしました";
    window.setTimeout(function () {
      copyRoomIdButton.textContent = "コピー";
    }, 2000);
  } catch (error) {
    copyStatus.textContent = "コピーできませんでした。IDを選択してコピーしてください";
  }
});

enterCreatedRoomButton.addEventListener("click", function () {
  if (latestCreatedRoom) enterPrivateRoom(latestCreatedRoom.roomId);
});
