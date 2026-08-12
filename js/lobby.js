"use strict";

const roomButtons = document.querySelectorAll(".enter-button");
const publicRoomCards = document.querySelectorAll(".room-card[data-room-theme]");
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
const publicRoomError = document.getElementById("public-room-error");
const joinRoomButton = joinRoomForm.querySelector("button[type='submit']");

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
    await loadPublicRooms();

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

/** ロビーの固定カードへ、DBに存在するpublic部屋の正式なroomIdを設定します。 */
async function loadPublicRooms() {
  const response = await fetch("/api/rooms/public", { credentials: "same-origin" });
  if (response.status === 401) {
    returnToLoginAfterRoomUnauthorized();
    return;
  }
  if (!response.ok) throw new Error("パブリック部屋を取得できませんでした");

  const rooms = await response.json();
  const assignmentCounts = new Map();
  await Promise.all(rooms.map(async function (room) {
    const assignmentsResponse = await fetch(
      `/api/rooms/${encodeURIComponent(room.roomId)}/seat-assignments`,
      { credentials: "same-origin" }
    );
    if (!assignmentsResponse.ok) {
      throw new Error("パブリック部屋の入室者数を取得できませんでした");
    }
    const assignments = await assignmentsResponse.json();
    assignmentCounts.set(Number(room.roomId), assignments.length);
  }));

  publicRoomCards.forEach(function (card) {
    const themeRooms = rooms.filter(function (room) {
      return room.theme === card.dataset.roomTheme;
    });
    const primaryRoom = themeRooms[0];
    const participantCount = themeRooms.reduce(function (total, room) {
      return total + (assignmentCounts.get(Number(room.roomId)) || 0);
    }, 0);
    const title = card.querySelector("[data-room-title]");
    const count = card.querySelector("[data-participant-count]");
    const notice = card.querySelector(".room-notice");

    if (title && primaryRoom) {
      title.textContent = primaryRoom.roomName
        + (themeRooms.length > 1 ? ` <1/${themeRooms.length}>` : "");
    }
    if (count) count.textContent = `入室者 ${participantCount}人`;
    if (notice) {
      const primaryCount = primaryRoom
        ? (assignmentCounts.get(Number(primaryRoom.roomId)) || 0) : 0;
      notice.hidden = !primaryRoom || primaryCount < primaryRoom.maxSeats;
    }
  });

  roomButtons.forEach(function (button) {
    const room = rooms.find(function (candidate) {
      return candidate.theme === button.dataset.theme;
    });
    button.dataset.roomId = room ? String(room.roomId) : "";
    button.dataset.roomName = room?.roomName || "";
    button.disabled = !room;
  });
}

async function logout() {
  try {
    await fetch("/api/users/logout", {
      method: "POST",
      credentials: "same-origin"
    });
  } finally {
    clearCurrentPrivateRoom();
    clearAuthenticatedUser();
    closeAvatarModal();
    showView("login");
  }
}

/**
 * 選択したテーマのパブリック部屋へ移動する仮処理です。
 * API導入後は、入室可能か確認してから移動する処理に置き換えます。
 */
async function enterRoom(button) {
  const roomId = button.dataset.roomId;
  if (!roomId) {
    publicRoomError.textContent = "現在入室できる部屋がありません";
    return;
  }

  publicRoomError.textContent = "";
  button.disabled = true;
  try {
    const response = await fetch(`/api/rooms/${encodeURIComponent(roomId)}/join`, {
      method: "POST",
      credentials: "same-origin"
    });
    if (response.status === 401) {
      returnToLoginAfterRoomUnauthorized();
      return;
    }
    const responseBody = await response.json();
    if (!response.ok) {
      throw new Error(responseBody.message || "部屋に参加できませんでした");
    }

    clearCurrentPrivateRoom();
    const query = new URLSearchParams({
      roomId: responseBody.roomId,
      theme: responseBody.theme,
      roomName: responseBody.roomName
    }).toString();
    showView("room", true, query);
  } catch (error) {
    publicRoomError.textContent = error.message;
  } finally {
    button.disabled = false;
  }
}

/** プライベート部屋作成画面へ移動します。 */
function openCreateRoomView() {
  showView("create-room");
}

/**
 * 入力された部屋IDを使って入室する仮処理です。
 * 現段階では部屋が実在するかどうかは確認しません。
 */
async function joinPrivateRoom() {
  const roomCode = roomIdInput.value.trim().toUpperCase();

  if (roomCode === "") {
    roomIdError.textContent = "部屋IDを入力してください";
    roomIdInput.setAttribute("aria-invalid", "true");
    roomIdInput.focus();
    return;
  }

  roomIdError.textContent = "";
  roomIdInput.removeAttribute("aria-invalid");
  roomIdInput.value = roomCode;

  try {
    joinRoomButton.disabled = true;
    const response = await fetch("/api/rooms/private/join", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ roomCode: roomCode })
    });

    if (response.status === 401) {
      returnToLoginAfterRoomUnauthorized();
      return;
    }
    const responseBody = await response.json();
    if (!response.ok) {
      roomIdError.textContent = responseBody.message || "部屋に参加できませんでした";
      roomIdInput.setAttribute("aria-invalid", "true");
      return;
    }

    enterPrivateRoom(responseBody);
  } catch (error) {
    roomIdError.textContent = "部屋に参加できませんでした";
    roomIdInput.setAttribute("aria-invalid", "true");
  } finally {
    joinRoomButton.disabled = false;
  }
}

// HTMLには処理を直接書かず、ここで各ボタンにイベントを設定します。
roomButtons.forEach(function (button) {
  button.addEventListener("click", function () {
    enterRoom(button);
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
