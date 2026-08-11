"use strict";

const workspaceRoomName = document.getElementById("workspace-room-name");
const workspaceParticipantCount = document.getElementById("workspace-participant-count");
const workspaceScene = document.getElementById("workspace-scene");
const roomBackgroundLayer = document.getElementById("room-background-layer");
const upperChairLayer = document.getElementById("upper-chair-layer");
const lowerChairLayer = document.getElementById("lower-chair-layer");
const upperParticipantLayer = document.getElementById("upper-participant-layer");
const lowerParticipantLayer = document.getElementById("lower-participant-layer");
const statusBadgeLayer = document.getElementById("status-badge-layer");
const workspaceTooltip = document.getElementById("workspace-tooltip");
const changeAvatarButton = document.getElementById("change-avatar-button");
const changeThemeButton = document.getElementById("change-theme-button");
const leaveRoomButton = document.getElementById("leave-room-button");
const statusButtons = document.querySelectorAll(".status-button");
const sideTabs = document.querySelectorAll(".side-tab");
const sidePanelContent = document.querySelector(".side-panel-content");
const selfPanel = document.getElementById("self-panel");
const chatPanel = document.getElementById("chat-panel");
const sideUsername = document.getElementById("side-username");
const sideElapsedTime = document.getElementById("side-elapsed-time");
const myWorkContentInput = document.getElementById("my-work-content");
const privateMemoInput = document.getElementById("private-memo");
const chatTargetSelect = document.getElementById("chat-target");
const chatHistoryTitle = document.getElementById("chat-history-title");
const chatHistory = document.getElementById("chat-history");
const chatMessageInput = document.getElementById("chat-message-input");
const sendChatButton = document.getElementById("send-chat-button");
const roomSettingsOverlay = document.getElementById("room-settings-overlay");
const closeRoomSettingsButton = document.getElementById("close-room-settings-button");
const creatorThemeSettings = document.getElementById("creator-theme-settings");
const readonlyThemeSettings = document.getElementById("readonly-theme-settings");
const currentThemeCard = document.getElementById("current-theme-card");
const settingsThemeInputs = document.querySelectorAll('input[name="settingsTheme"]');
const saveRoomThemeButton = document.getElementById("save-room-theme-button");

const roomThemeNames = {
  focus: "静かに集中室",
  casual: "雑談OK部屋",
  night: "深夜勢の部屋"
};

const roomBackgrounds = {
  focus: "work-space-pic/room-forcus-task.png",
  casual: "work-space-pic/room-speak-ok.png",
  night: "work-space-pic/room-midnight-task.PNG"
};

const roomSettingsThemeLabels = {
  focus: "📗 集中部屋",
  casual: "💬 雑談OK部屋",
  night: "🌙 深夜勢の部屋"
};

const roomAvatarImages = {
  male_a: "work-space-pic/avatar-man-A.png",
  male_b: "work-space-pic/avatar-man-B.png",
  female_a: "work-space-pic/avatar-woman-A.png",
  female_b: "work-space-pic/avatar-woman-B.png"
};

let currentRoomInfo = null;
let myStatus = "working";
let myWorkContent = "";
let privateMemo = "";
let roomEnteredAt = null;
let currentParticipants = [];
let selectedChatTarget = "all";
let selectedRoomTheme = null;

// 全体チャットと個別チャットの履歴は、送信先ごとに分けて管理します。
const chatHistories = {
  all: [
    { senderId: "seat-upper-2", senderName: "上段2", content: "今日もよろしくお願いします" },
    { senderId: "me", senderName: "自分", content: "よろしくお願いします" }
  ],
  "seat-upper-2": [
    { senderId: "seat-upper-2", senderName: "上段2", content: "Javaのエラーについて質問したいです" },
    { senderId: "me", senderName: "自分", content: "見てみます" }
  ]
};

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
  sidePanelContent.classList.toggle("is-chat-active", !showSelf);

  sideTabs.forEach(function (tab) {
    const isActive = tab.dataset.sideTab === tabName;
    tab.classList.toggle("is-active", isActive);
    tab.setAttribute("aria-selected", String(isActive));
  });

  if (!showSelf) {
    renderChatHistory();
    updateChatPlaceholder();
    scrollChatToBottom();
  }
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

/** 現在開いている保存済みプライベート部屋を取得します。 */
function getCurrentPrivateRoom() {
  if (!currentRoomInfo?.isPrivate || !currentRoomInfo.roomId) return null;
  return findPrivateRoomById(currentRoomInfo.roomId) || null;
}

/** 作成者判定を一か所で行います。 */
function isCurrentUserRoomCreator(room = getCurrentPrivateRoom()) {
  return Boolean(room && room.createdBy === getWorkspaceUsername());
}

/** 作成者または閲覧者に合わせてモーダル内容を描画します。 */
function renderRoomSettings() {
  const room = getCurrentPrivateRoom();
  if (!room) return;

  const isCreator = isCurrentUserRoomCreator(room);
  selectedRoomTheme = room.theme;
  creatorThemeSettings.hidden = !isCreator;
  readonlyThemeSettings.hidden = isCreator;

  if (isCreator) {
    settingsThemeInputs.forEach(function (input) {
      input.checked = input.value === room.theme;
    });
  } else {
    currentThemeCard.textContent = roomSettingsThemeLabels[room.theme] || "テーマ未設定";
  }
}

/** プライベート部屋の設定モーダルを表示します。 */
function openRoomSettingsModal() {
  if (!getCurrentPrivateRoom()) return;
  renderRoomSettings();
  roomSettingsOverlay.hidden = false;
  document.body.classList.add("has-open-modal");
  closeRoomSettingsButton.focus();
}

/** 未保存の選択を破棄してモーダルを閉じます。 */
function closeRoomSettingsModal() {
  roomSettingsOverlay.hidden = true;
  document.body.classList.remove("has-open-modal");
  selectedRoomTheme = null;
  changeThemeButton.focus();
}

/** 保存前の一時的なテーマ選択を保持します。 */
function selectRoomTheme(theme) {
  if (roomSettingsThemeLabels[theme]) selectedRoomTheme = theme;
}

/** 保存されたテーマを再読み込みなしでワークスペースへ反映します。 */
function applyRoomTheme(theme) {
  if (!currentRoomInfo || !roomSettingsThemeLabels[theme]) return;
  currentRoomInfo.theme = theme;
  renderWorkspace(currentRoomInfo);
}

/** 作成者が保存を押したときだけlocalStorageのテーマを更新します。 */
function saveRoomTheme() {
  const room = getCurrentPrivateRoom();
  if (!room || !isCurrentUserRoomCreator(room) || !selectedRoomTheme) return;

  const privateRooms = getPrivateRooms();
  const targetRoom = privateRooms.find(function (privateRoom) {
    return privateRoom.roomId === room.roomId;
  });
  if (!targetRoom) return;

  targetRoom.theme = selectedRoomTheme;
  savePrivateRooms(privateRooms);
  applyRoomTheme(selectedRoomTheme);
  closeRoomSettingsModal();
}

/** UI確認用の入室者データを返します。後からAPI取得へ置き換えます。 */
function getParticipants(roomInfo) {
  const username = getWorkspaceUsername();
  const myAvatar = normalizeAvatarType(localStorage.getItem(`avatarType:${username}`)) || "male_a";
  // 全テーマ共通の10席です。アバター位置はこのx・yだけで調整できます。
  const seatPositions = [
    { seatId: "upper-1", x: 26, y: 31.6 },
    { seatId: "upper-2", x: 37, y: 35 },
    { seatId: "upper-3", x: 48, y: 38 },
    { seatId: "upper-4", x: 58.8, y: 40.8 },
    { seatId: "upper-5", x: 69, y: 44 },
    { seatId: "lower-1", x: 22, y: 56 },
    { seatId: "lower-2", x: 32, y: 59 },
    { seatId: "lower-3", x: 43, y: 62 },
    { seatId: "lower-4", x: 54, y: 65 },
    { seatId: "lower-5", x: 65, y: 69 }
  ];
  const participants = [
    { id: "me", name: username, avatarType: myAvatar, status: myStatus, elapsedTime: sideElapsedTime.textContent, memo: myWorkContent, joinOrder: 0, isMe: true },
    { id: "seat-upper-2", name: "上段2", avatarType: "male_b", status: "working", elapsedTime: "35分", memo: "Java学習", joinOrder: 1 },
    { id: "seat-upper-3", name: "上段3", avatarType: "female_a", status: "break", elapsedTime: "1時間05分", memo: "", joinOrder: 2 },
    { id: "seat-upper-4", name: "上段4", avatarType: "female_b", status: "working", elapsedTime: "48分", memo: "資料作成", joinOrder: 3 },
    { id: "seat-upper-5", name: "上段5", avatarType: "male_a", status: "working", elapsedTime: "1時間20分", memo: "ポートフォリオ制作", joinOrder: 4 },
    { id: "seat-lower-1", name: "下段1", avatarType: "female_b", status: "working", elapsedTime: "22分", memo: "デザイン確認", joinOrder: 5 },
    { id: "seat-lower-2", name: "下段2", avatarType: "male_b", status: "break", elapsedTime: "50分", memo: "休憩中", joinOrder: 6 },
    { id: "seat-lower-3", name: "下段3", avatarType: "female_a", status: "working", elapsedTime: "18分", memo: "資格勉強", joinOrder: 7 },
    { id: "seat-lower-4", name: "下段4", avatarType: "male_a", status: "working", elapsedTime: "42分", memo: "コーディング", joinOrder: 8 },
    { id: "seat-lower-5", name: "下段5", avatarType: "female_b", status: "working", elapsedTime: "27分", memo: "読書", joinOrder: 9 }
  ];
  const positionedParticipants = participants.map(function (participant, index) {
    return { ...participant, ...seatPositions[index] };
  });
  return positionedParticipants;
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
  upperParticipantLayer.replaceChildren();
  lowerParticipantLayer.replaceChildren();
  statusBadgeLayer.replaceChildren();

  participants.forEach(function (participant) {
    const participantElement = document.createElement("button");
    participantElement.type = "button";
    participantElement.className = "participant";
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
    participantElement.addEventListener("click", function () {
      if (!participant.isMe) selectChatTarget(participant.id);
    });
    const targetLayer = participant.seatId.startsWith("upper-")
      ? upperParticipantLayer
      : lowerParticipantLayer;
    targetLayer.appendChild(participantElement);

    const statusBadge = document.createElement("span");
    statusBadge.className = `participant-status-badge is-${participant.status}`;
    statusBadge.style.left = `${participant.x}%`;
    statusBadge.style.top = `${participant.y}%`;
    statusBadgeLayer.appendChild(statusBadge);
  });
}

/** 現在の参加者一覧から、入室順にチャット送信先を作成します。 */
function populateChatTargets(participants) {
  const otherParticipants = participants
    .filter(function (participant) { return !participant.isMe; })
    .sort(function (first, second) { return first.joinOrder - second.joinOrder; });

  chatTargetSelect.replaceChildren();
  const allOption = document.createElement("option");
  allOption.value = "all";
  allOption.textContent = "全体";
  chatTargetSelect.appendChild(allOption);

  otherParticipants.forEach(function (participant) {
    const option = document.createElement("option");
    option.value = participant.id;
    option.textContent = `${participant.name}さん`;
    chatTargetSelect.appendChild(option);
    if (!chatHistories[participant.id]) chatHistories[participant.id] = [];
  });

  if (![...chatTargetSelect.options].some(function (option) {
    return option.value === selectedChatTarget;
  })) {
    selectedChatTarget = "all";
  }
  chatTargetSelect.value = selectedChatTarget;
}

/** userIdから参加者を取得します。 */
function getChatTargetParticipant(userId) {
  return currentParticipants.find(function (participant) {
    return participant.id === userId && !participant.isMe;
  });
}

/** 選択中の送信先に対応する履歴を安全なDOM操作で描画します。 */
function renderChatHistory() {
  const targetParticipant = getChatTargetParticipant(selectedChatTarget);
  chatHistoryTitle.textContent = selectedChatTarget === "all"
    ? "全体チャット"
    : `${targetParticipant?.name || "ユーザー"}さんとの個別チャット`;
  chatHistory.replaceChildren();

  const messages = chatHistories[selectedChatTarget] || [];
  if (messages.length === 0) {
    const emptyMessage = document.createElement("p");
    emptyMessage.className = "chat-empty-message";
    emptyMessage.textContent = "まだメッセージはありません";
    chatHistory.appendChild(emptyMessage);
    return;
  }

  messages.forEach(function (message) {
    const messageElement = document.createElement("p");
    const senderElement = document.createElement("strong");
    messageElement.className = "chat-message";
    senderElement.textContent = `${message.senderName}: `;
    messageElement.append(senderElement, document.createTextNode(message.content));
    chatHistory.appendChild(messageElement);
  });
}

/** 選択中の相手に応じて入力欄の案内を変更します。 */
function updateChatPlaceholder() {
  const targetParticipant = getChatTargetParticipant(selectedChatTarget);
  chatMessageInput.placeholder = selectedChatTarget === "all"
    ? "全体へメッセージを送る"
    : `${targetParticipant?.name || "相手"}さんへメッセージを送る`;
}

/** チャット履歴の最下部を表示します。 */
function scrollChatToBottom() {
  window.requestAnimationFrame(function () {
    chatHistory.scrollTop = chatHistory.scrollHeight;
  });
}

/** アバターまたはプルダウンから個別チャット相手を選択します。 */
function selectChatTarget(userId) {
  if (userId === "me") return;
  const targetExists = userId === "all" || Boolean(getChatTargetParticipant(userId));
  if (!targetExists) return;

  selectedChatTarget = userId;
  chatTargetSelect.value = userId;
  switchSideTab("chat");
  renderChatHistory();
  updateChatPlaceholder();
  scrollChatToBottom();
}

/** 指定された履歴へメッセージを追加します。 */
function addChatMessage(targetId, message) {
  if (!chatHistories[targetId]) chatHistories[targetId] = [];
  chatHistories[targetId].push(message);
}

/** Enterまたは送信ボタンから呼び出す共通送信処理です。 */
function sendChatMessage() {
  const message = chatMessageInput.value.trim();
  if (!message) return;

  addChatMessage(selectedChatTarget, {
    senderId: "me",
    senderName: getWorkspaceUsername(),
    content: message,
    sentAt: new Date()
  });
  chatMessageInput.value = "";
  renderChatHistory();
  scrollChatToBottom();
}

/** 部屋情報とレイヤー画像を画面へ反映します。 */
function renderWorkspace(roomInfo) {
  const participants = getParticipants(roomInfo);
  currentParticipants = participants;
  workspaceRoomName.textContent = roomInfo.roomName;
  workspaceParticipantCount.textContent = `入室者 ${participants.length}人`;
  workspaceScene.dataset.theme = roomInfo.theme;
  roomBackgroundLayer.src = roomBackgrounds[roomInfo.theme];
  roomBackgroundLayer.alt = `${roomInfo.roomName}の背景`;

  // 集中部屋の背景には椅子が描かれているため、分割椅子を重ねません。
  const hideChairLayers = roomInfo.theme === "focus";
  upperChairLayer.hidden = hideChairLayers;
  lowerChairLayer.hidden = hideChairLayers;
  changeThemeButton.hidden = !roomInfo.isPrivate;
  renderParticipants(participants);
  populateChatTargets(participants);
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

/** URLに対応する部屋を初期表示します。 */
function initializeRoom(queryString) {
  currentRoomInfo = getRoomInfo(queryString);
  myStatus = "working";
  roomEnteredAt = Date.now();
  loadMyWorkspaceState();
  selectedChatTarget = "all";
  switchSideTab("self");
  updateElapsedTime();
  setMyStatus(myStatus);
  renderWorkspace(currentRoomInfo);
}

changeAvatarButton.addEventListener("click", openAvatarModal);
changeThemeButton.addEventListener("click", openRoomSettingsModal);
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

chatTargetSelect.addEventListener("change", function () {
  selectChatTarget(chatTargetSelect.value);
});

sendChatButton.addEventListener("click", sendChatMessage);

chatMessageInput.addEventListener("keydown", function (event) {
  if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    sendChatMessage();
  }
});

settingsThemeInputs.forEach(function (input) {
  input.addEventListener("change", function () {
    selectRoomTheme(input.value);
  });
});

saveRoomThemeButton.addEventListener("click", saveRoomTheme);
closeRoomSettingsButton.addEventListener("click", closeRoomSettingsModal);

roomSettingsOverlay.addEventListener("click", function (event) {
  if (event.target === roomSettingsOverlay) closeRoomSettingsModal();
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
