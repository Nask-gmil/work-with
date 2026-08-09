"use strict";

const workspaceRoomName = document.getElementById("workspace-room-name");
const workspaceParticipantCount = document.getElementById("workspace-participant-count");
const roomBackgroundLayer = document.getElementById("room-background-layer");
const chairLayer = document.getElementById("chair-layer");
const participantLayer = document.getElementById("participant-layer");
const workspaceTooltip = document.getElementById("workspace-tooltip");
const changeAvatarButton = document.getElementById("change-avatar-button");
const changeThemeButton = document.getElementById("change-theme-button");
const leaveRoomButton = document.getElementById("leave-room-button");
const statusButtons = document.querySelectorAll(".status-button");
const sideTabs = document.querySelectorAll(".side-tab");
const selfPanel = document.getElementById("self-panel");
const chatPanel = document.getElementById("chat-panel");
const sideUsername = document.getElementById("side-username");
const sideElapsedTime = document.getElementById("side-elapsed-time");
const myWorkContentInput = document.getElementById("my-work-content");
const privateMemoInput = document.getElementById("private-memo");

const roomThemeNames = {
  focus: "静かに集中室",
  casual: "雑談OK部屋",
  night: "深夜勢の部屋"
};

const roomBackgrounds = {
  focus: "work-space-pic/集中部屋.png",
  casual: "work-space-pic/雑談OK部屋.png",
  night: "work-space-pic/深夜部屋.PNG"
};

const roomAvatarImages = {
  maleA: "work-space-pic/final_アバター_男性A.png",
  maleB: "work-space-pic/final_アバター_男性B.png",
  femaleA: "work-space-pic/final_アバター_女性A.png",
  femaleB: "work-space-pic/final_アバター_女性B.png"
};

let currentRoomInfo = null;
let myStatus = "working";
let myWorkContent = "";
let privateMemo = "";
let roomEnteredAt = null;

/** 現在のユーザー名を取得します。 */
function getWorkspaceUsername() {
  return sessionStorage.getItem("username") || "ゲスト";
}

/** ユーザーごとに保存された作業内容と個人メモを復元します。 */
function loadMyWorkspaceState() {
  const username = getWorkspaceUsername();
  myWorkContent = localStorage.getItem(`workContent:${username}`) || "";
  privateMemo = localStorage.getItem(`privateMemo:${username}`) || "";
  sideUsername.textContent = username;
  myWorkContentInput.value = myWorkContent;
  privateMemoInput.value = privateMemo;
}

/** 他の利用者にも見える作業内容を仮保存します。 */
function saveWorkContent(workContent) {
  const username = getWorkspaceUsername();
  myWorkContent = workContent;
  localStorage.setItem(`workContent:${username}`, workContent);
}

/** 自分だけに表示する個人メモを仮保存します。 */
function savePrivateMemo(memo) {
  const username = getWorkspaceUsername();
  privateMemo = memo;
  localStorage.setItem(`privateMemo:${username}`, memo);
}

/** 入室時刻からの経過時間を「1時間20分」形式で更新します。 */
function updateElapsedTime() {
  if (!roomEnteredAt) return;

  const elapsedMinutes = Math.floor((Date.now() - roomEnteredAt) / 60000);
  const hours = Math.floor(elapsedMinutes / 60);
  const minutes = elapsedMinutes % 60;
  sideElapsedTime.textContent = hours > 0 ? `${hours}時間${minutes}分` : `${minutes}分`;
}

/** 自分・チャットの表示をHTMLを作り直さず切り替えます。 */
function switchSideTab(tabName) {
  const showSelf = tabName === "self";
  selfPanel.hidden = !showSelf;
  chatPanel.hidden = showSelf;

  sideTabs.forEach(function (tab) {
    const isActive = tab.dataset.sideTab === tabName;
    tab.classList.toggle("is-active", isActive);
    tab.setAttribute("aria-selected", String(isActive));
  });
}

/** URLSearchParamsと保存済みデータから表示する部屋情報を取得します。 */
function getRoomInfo(queryString) {
  const params = new URLSearchParams(queryString);
  const roomId = params.get("roomId");
  const requestedTheme = params.get("theme");
  const privateRoom = roomId ? findPrivateRoomById(roomId) : null;

  if (privateRoom) {
    return {
      roomId: privateRoom.roomId,
      roomName: privateRoom.roomName,
      theme: privateRoom.theme,
      createdBy: privateRoom.createdBy,
      isPrivate: true
    };
  }

  if (roomThemeNames[requestedTheme]) {
    return {
      roomId: null,
      roomName: roomThemeNames[requestedTheme],
      theme: requestedTheme,
      createdBy: null,
      isPrivate: false
    };
  }

  return { roomId: null, roomName: "部屋", theme: "focus", createdBy: null, isPrivate: false };
}

/** UI確認用の入室者データを返します。後からAPI取得へ置き換えます。 */
function getParticipants(roomInfo) {
  const username = getWorkspaceUsername();
  const myAvatar = localStorage.getItem(`avatarType:${username}`) || "maleA";
  const participants = [
    { id: "me", name: username, avatarType: myAvatar, status: myStatus, elapsedTime: sideElapsedTime.textContent, memo: myWorkContent, x: 18, y: 47, isMe: true },
    { id: 2, name: "佐藤", avatarType: "maleB", status: "working", elapsedTime: "35分", memo: "Java学習", x: 34, y: 47 },
    { id: 3, name: "鈴木", avatarType: "femaleA", status: "break", elapsedTime: "1時間05分", memo: "", x: 50, y: 47 },
    { id: 4, name: "高橋", avatarType: "femaleB", status: "working", elapsedTime: "48分", memo: "資料作成", x: 66, y: 47 },
    { id: 5, name: "田中", avatarType: "maleA", status: "working", elapsedTime: "1時間20分", memo: "ポートフォリオ制作", x: 82, y: 47 },
    { id: 6, name: "伊藤", avatarType: "femaleB", status: "working", elapsedTime: "22分", memo: "デザイン確認", x: 16, y: 72 },
    { id: 7, name: "渡辺", avatarType: "maleB", status: "break", elapsedTime: "50分", memo: "休憩中", x: 34, y: 72 },
    { id: 8, name: "山本", avatarType: "femaleA", status: "working", elapsedTime: "18分", memo: "資格勉強", x: 51, y: 72 },
    { id: 9, name: "中村", avatarType: "maleA", status: "working", elapsedTime: "42分", memo: "コーディング", x: 68, y: 72 },
    { id: 10, name: "小林", avatarType: "femaleB", status: "working", elapsedTime: "27分", memo: "読書", x: 84, y: 72 }
  ];
  const participantCounts = { focus: 7, casual: 10, night: 2 };
  return participants.slice(0, participantCounts[roomInfo.theme] || 7);
}

/** アバターのホバー情報を最前面のUIレイヤーへ表示します。 */
function showAvatarTooltip(participant) {
  const statusLabel = participant.status === "working" ? "作業中" : "休憩中";
  const memoLabel = participant.memo || "なし";
  const lines = [
    { text: `${participant.name}さん`, strong: true },
    { text: `状態: ${statusLabel}` },
    { text: `経過: ${participant.elapsedTime}` },
    { text: `メモ: ${memoLabel}` }
  ];

  workspaceTooltip.replaceChildren();
  lines.forEach(function (line) {
    const element = document.createElement(line.strong ? "strong" : "span");
    element.textContent = line.text;
    workspaceTooltip.appendChild(element);
  });
  workspaceTooltip.style.left = `${participant.x}%`;
  workspaceTooltip.style.top = `${participant.y}%`;
  workspaceTooltip.hidden = false;
}

function hideAvatarTooltip() {
  workspaceTooltip.hidden = true;
}

/** 入室者を指定座標へ配置します。 */
function renderParticipants(participants) {
  participantLayer.replaceChildren();

  participants.forEach(function (participant) {
    const participantElement = document.createElement("button");
    participantElement.type = "button";
    participantElement.className = `participant${participant.status === "break" ? " is-break" : ""}`;
    participantElement.style.left = `${participant.x}%`;
    participantElement.style.top = `${participant.y}%`;
    participantElement.setAttribute("aria-label", `${participant.name}さんの情報`);
    const avatarImage = document.createElement("img");
    avatarImage.src = roomAvatarImages[participant.avatarType];
    avatarImage.alt = "";
    participantElement.appendChild(avatarImage);
    participantElement.addEventListener("mouseenter", function () {
      showAvatarTooltip(participant);
    });
    participantElement.addEventListener("mouseleave", hideAvatarTooltip);
    participantElement.addEventListener("focus", function () {
      showAvatarTooltip(participant);
    });
    participantElement.addEventListener("blur", hideAvatarTooltip);
    participantLayer.appendChild(participantElement);
  });
}

/** 部屋情報とレイヤー画像を画面へ反映します。 */
function renderWorkspace(roomInfo) {
  const participants = getParticipants(roomInfo);
  workspaceRoomName.textContent = roomInfo.roomName;
  workspaceParticipantCount.textContent = `入室者 ${participants.length}人`;
  roomBackgroundLayer.src = roomBackgrounds[roomInfo.theme];
  roomBackgroundLayer.alt = `${roomInfo.roomName}の背景`;

  // 集中部屋の背景には椅子が描かれているため、椅子レイヤーを重ねません。
  chairLayer.hidden = roomInfo.theme === "focus";
  changeThemeButton.hidden = !roomInfo.isPrivate;
  renderParticipants(participants);
}

/** 自分の状態だけを画面上で切り替えます。 */
function setMyStatus(status) {
  myStatus = status;
  statusButtons.forEach(function (button) {
    button.classList.toggle("is-active", button.dataset.status === status);
  });

  if (currentRoomInfo) renderParticipants(getParticipants(currentRoomInfo));
}

/** 既存のアバター選択モーダルを部屋画面から開きます。 */
function openAvatarModal() {
  showAvatarModal();
}

/** 将来テーマ変更UIを接続するための仮関数です。 */
function handleThemeChange() {
  // Spring Boot導入後にテーマ更新処理を追加します。
}

/** URLに対応する部屋を初期表示します。 */
function initializeRoom(queryString) {
  currentRoomInfo = getRoomInfo(queryString);
  myStatus = "working";
  roomEnteredAt = Date.now();
  loadMyWorkspaceState();
  switchSideTab("self");
  updateElapsedTime();
  setMyStatus(myStatus);
  renderWorkspace(currentRoomInfo);
}

changeAvatarButton.addEventListener("click", openAvatarModal);
changeThemeButton.addEventListener("click", handleThemeChange);
leaveRoomButton.addEventListener("click", function () {
  showView("lobby");
});

statusButtons.forEach(function (button) {
  button.addEventListener("click", function () {
    setMyStatus(button.dataset.status);
  });
});

sideTabs.forEach(function (tab) {
  tab.addEventListener("click", function () {
    switchSideTab(tab.dataset.sideTab);
  });
});

myWorkContentInput.addEventListener("input", function () {
  saveWorkContent(myWorkContentInput.value);
  if (currentRoomInfo) renderParticipants(getParticipants(currentRoomInfo));
});

privateMemoInput.addEventListener("input", function () {
  // 個人メモは保存するだけで、アバターやツールチップには渡しません。
  savePrivateMemo(privateMemoInput.value);
});

document.addEventListener("avatarupdated", function () {
  if (currentRoomInfo) renderParticipants(getParticipants(currentRoomInfo));
});

document.addEventListener("viewchange", function (event) {
  if (event.detail.view === "room") initializeRoom(event.detail.query);
});

// main.html#room?...を直接開いた場合にも部屋情報を表示します。
if (!document.getElementById("room-view").hidden) {
  initializeRoom(getQueryFromUrl());
}

// サーバー時刻は使わず、UI確認用として1分ごとに表示を更新します。
window.setInterval(function () {
  updateElapsedTime();
  if (currentRoomInfo && !document.getElementById("room-view").hidden) {
    renderParticipants(getParticipants(currentRoomInfo));
  }
}, 60000);
