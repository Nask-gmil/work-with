"use strict";

const createRoomForm = document.getElementById("create-room-form");
const privateRoomNameInput = document.getElementById("private-room-name");
const createdRoomResult = document.getElementById("created-room-result");
const createdRoomName = document.getElementById("created-room-name");
const createdRoomId = document.getElementById("created-room-id");
const copyRoomIdButton = document.getElementById("copy-room-id-button");
const copyStatus = document.getElementById("copy-status");
const enterCreatedRoomButton = document.getElementById("enter-created-room-button");
const createRoomError = document.getElementById("create-room-error");
const createRoomSubmitButton = createRoomForm.querySelector("button[type='submit']");

const currentPrivateRoomKey = "currentPrivateRoom";
let latestCreatedRoom = null;

/** ワークスペース表示用の部屋情報だけを、現在のタブへ一時保存します。 */
function saveCurrentPrivateRoom(room) {
  const currentRoom = {
    roomId: room.roomId,
    roomCode: room.roomCode,
    roomName: room.roomName,
    theme: room.theme,
    isCreator: room.isCreator === true
      || room.createdBy === getAuthenticatedUser()?.userId,
    roomType: room.roomType
  };
  sessionStorage.setItem(currentPrivateRoomKey, JSON.stringify(currentRoom));
  return currentRoom;
}

function loadCurrentPrivateRoom() {
  try {
    return JSON.parse(sessionStorage.getItem(currentPrivateRoomKey));
  } catch (error) {
    return null;
  }
}

function clearCurrentPrivateRoom() {
  sessionStorage.removeItem(currentPrivateRoomKey);
}

function returnToLoginAfterRoomUnauthorized() {
  clearCurrentPrivateRoom();
  clearAuthenticatedUser();
  showView("login");
}

/** 入力内容をSpring Bootへ送り、SQLiteにprivate部屋を作成します。 */
async function createPrivateRoom() {
  const response = await fetch("/api/rooms", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      roomName: privateRoomNameInput.value.trim(),
      theme: createRoomForm.elements.roomTheme.value,
      maxSeats: 10
    })
  });

  if (response.status === 401) {
    returnToLoginAfterRoomUnauthorized();
    return null;
  }

  const responseBody = await response.json();
  if (!response.ok) {
    const message = response.status === 400
      ? responseBody.message || "入力内容を確認してください"
      : "部屋を作成できませんでした";
    throw new Error(message);
  }

  return saveCurrentPrivateRoom(responseBody);
}

/** Spring Bootから返された部屋名と参加用roomCodeを表示します。 */
function showCreatedRoom(room) {
  latestCreatedRoom = room;
  createdRoomName.textContent = room.roomName;
  createdRoomId.textContent = room.roomCode;
  copyStatus.textContent = "";
  copyRoomIdButton.textContent = "コピー";
  createdRoomResult.hidden = false;
  createdRoomResult.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

/** 既存SPAのワークスペース画面へ移動します。 */
function enterPrivateRoom(room) {
  const currentRoom = saveCurrentPrivateRoom(room);
  const query = new URLSearchParams({ roomId: currentRoom.roomId }).toString();
  showView("room", true, query);
}

createRoomForm.addEventListener("submit", async function (event) {
  event.preventDefault();
  createRoomError.textContent = "";
  createRoomSubmitButton.disabled = true;

  try {
    const room = await createPrivateRoom();
    if (room) showCreatedRoom(room);
  } catch (error) {
    createRoomError.textContent = error.message;
  } finally {
    createRoomSubmitButton.disabled = false;
  }
});

copyRoomIdButton.addEventListener("click", async function () {
  if (!latestCreatedRoom) return;

  try {
    await navigator.clipboard.writeText(latestCreatedRoom.roomCode);
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
  if (latestCreatedRoom) enterPrivateRoom(latestCreatedRoom);
});
