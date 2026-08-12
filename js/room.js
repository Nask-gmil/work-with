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
const leaveRoomError = document.getElementById("leave-room-error");
const statusButtons = document.querySelectorAll(".status-button");
const statusError = document.getElementById("status-error");
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
let roomStompClient = null;

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
  return getAuthenticatedUser()?.username || "ゲスト";
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
  const requestedRoomName = params.get("roomName");
  const savedPrivateRoom = roomId ? loadCurrentPrivateRoom() : null;
  const privateRoom = savedPrivateRoom
    && String(savedPrivateRoom.roomId) === roomId
    ? savedPrivateRoom
    : null;

  if (privateRoom) {
    return {
      roomId: privateRoom.roomId,
      roomCode: privateRoom.roomCode,
      roomName: privateRoom.roomName,
      theme: privateRoom.theme,
      isCreator: privateRoom.isCreator,
      isPrivate: true
    };
  }

  if (roomThemeNames[requestedTheme]) {
    return {
      roomId: roomId ? Number(roomId) : null,
      roomName: requestedRoomName || roomThemeNames[requestedTheme],
      theme: requestedTheme,
      isCreator: false,
      isPrivate: false
    };
  }

  return {
    roomId: roomId ? Number(roomId) : null,
    roomName: "部屋",
    theme: "focus",
    isCreator: false,
    isPrivate: false
  };
}

/** 現在開いている保存済みプライベート部屋を取得します。 */
function getCurrentPrivateRoom() {
  if (!currentRoomInfo?.isPrivate || !currentRoomInfo.roomId) return null;
  const room = loadCurrentPrivateRoom();
  return room && room.roomId === currentRoomInfo.roomId ? room : null;
}

/** 作成者判定を一か所で行います。 */
function isCurrentUserRoomCreator(room = getCurrentPrivateRoom()) {
  return Boolean(room?.isCreator);
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

  // テーマ更新APIは未実装のため、現在表示中のタブだけ一時的に反映します。
  room.theme = selectedRoomTheme;
  saveCurrentPrivateRoom(room);
  applyRoomTheme(selectedRoomTheme);
  closeRoomSettingsModal();
}

/** API失敗を共通処理し、認証切れならログイン画面へ戻します。 */
async function readRoomApi(response, fallbackMessage) {
  if (response.status === 401) {
    clearAuthenticatedUser();
    showView("login");
    throw new Error("ログインが必要です");
  }
  if (!response.ok) {
    const errorBody = await response.json().catch(function () { return null; });
    throw new Error(errorBody?.message || fallbackMessage);
  }
  return response.json();
}

/** SEATSとSEAT_ASSIGNMENTSをseatIdで結び、public/private共通の参加者を作ります。 */
async function loadRoomParticipants(roomId) {
  if (!roomId) return [];

  const [seatsResponse, assignmentsResponse] = await Promise.all([
    fetch(`/api/rooms/${encodeURIComponent(roomId)}/seats`),
    fetch(`/api/rooms/${encodeURIComponent(roomId)}/seat-assignments`)
  ]);
  const seats = await readRoomApi(seatsResponse, "座席情報を取得できませんでした");
  const assignments = await readRoomApi(
    assignmentsResponse,
    "入室者情報を取得できませんでした"
  );
  const seatMap = new Map(seats.map(function (seat) {
    return [String(seat.seatId), seat];
  }));
  const loginUserId = Number(getAuthenticatedUser()?.userId);

  return assignments.flatMap(function (assignment, index) {
    const seat = seatMap.get(String(assignment.seatId));
    if (!seat) return [];
    return [{
      id: String(assignment.userId),
      name: assignment.username,
      avatarType: normalizeAvatarType(assignment.avatarType) || "male_a",
      status: assignment.status,
      elapsedTime: formatElapsedTime(assignment.startedAt),
      memo: assignment.workContent || "",
      joinOrder: index,
      isMe: Number(assignment.userId) === loginUserId,
      seatId: assignment.seatId,
      seatNumber: seat.seatNumber,
      x: seat.posX,
      y: seat.posY
    }];
  });
}

/** 現在の部屋の参加者をREST APIから再取得して描画します。 */
async function refreshRoomParticipants(roomId) {
  if (!currentRoomInfo || currentRoomInfo.roomId !== roomId) return;
  const participants = await loadRoomParticipants(roomId);
  if (!currentRoomInfo || currentRoomInfo.roomId !== roomId) return;
  currentParticipants = participants;
  renderWorkspace(currentRoomInfo, currentParticipants);
}

/** SPAで残った購読と接続を終了します。座席の退席処理は行いません。 */
function disconnectRoomWebSocket() {
  if (!roomStompClient) return;
  const client = roomStompClient;
  roomStompClient = null;
  client.deactivate().catch(function (error) {
    console.error("WebSocketの切断に失敗しました", error);
  });
}

/** 指定した部屋だけを購読し、変更通知時にはREST APIを正として再取得します。 */
function connectRoomWebSocket(roomId) {
  disconnectRoomWebSocket();
  if (!window.StompJs?.Client) {
    console.error("STOMPクライアントを読み込めなかったため、リアルタイム更新を開始できません");
    return;
  }

  const socketProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const client = new window.StompJs.Client({
    brokerURL: `${socketProtocol}//${window.location.host}/ws`,
    reconnectDelay: 0,
    onConnect: function () {
      if (roomStompClient !== client || currentRoomInfo?.roomId !== roomId) return;
      client.subscribe(`/topic/room/${roomId}`, function (message) {
        try {
          const event = JSON.parse(message.body);
          if (event.type !== "participants-changed" || event.roomId !== roomId) return;
          refreshRoomParticipants(roomId).catch(function (error) {
            console.error("参加者一覧の再取得に失敗しました", error);
          });
        } catch (error) {
          console.error("WebSocket通知の解析に失敗しました", error);
        }
      });
    },
    onStompError: function (frame) {
      console.error("WebSocketのSTOMPエラー", frame.headers.message);
    },
    onWebSocketError: function (error) {
      console.error("WebSocket接続に失敗しました", error);
    }
  });
  roomStompClient = client;
  client.activate();
}

function formatElapsedTime(startedAt) {
  const startedTime = Date.parse(startedAt);
  if (!Number.isFinite(startedTime)) return "0分";
  const elapsedMinutes = Math.max(0, Math.floor((Date.now() - startedTime) / 60000));
  const hours = Math.floor(elapsedMinutes / 60);
  const minutes = elapsedMinutes % 60;
  return hours > 0 ? `${hours}時間${minutes}分` : `${minutes}分`;
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
    participantElement.dataset.userId = participant.id;
    participantElement.dataset.seatId = String(participant.seatId);
    const targetLayer = participant.seatNumber <= 5
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
function renderWorkspace(roomInfo, participants = currentParticipants) {
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

/** サーバーで確定した自分の状態を画面へ反映します。 */
function setMyStatus(status) {
  myStatus = status;
  statusButtons.forEach(function (button) {
    button.classList.toggle("is-active", button.dataset.status === status);
  });
}

/** ログイン中のユーザー本人の状態を更新します。 */
async function updateMyStatus(status) {
  if (!currentRoomInfo?.roomId) return;

  statusError.textContent = "";
  statusButtons.forEach(function (button) { button.disabled = true; });
  try {
    const response = await fetch(
      `/api/rooms/${encodeURIComponent(currentRoomInfo.roomId)}/seat-assignments/me/status`,
      {
        method: "PATCH",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status })
      }
    );
    if (response.status === 401) {
      clearCurrentPrivateRoom();
      clearAuthenticatedUser();
      showView("login");
      return;
    }
    const responseBody = await response.json().catch(function () { return null; });
    if (!response.ok) {
      throw new Error(responseBody?.message || "状態の更新に失敗しました");
    }

    currentParticipants = currentParticipants.map(function (participant) {
      return participant.isMe
        ? { ...participant, status: responseBody.status }
        : participant;
    });
    setMyStatus(responseBody.status);
    renderParticipants(currentParticipants);
  } catch (error) {
    statusError.textContent = error.message;
  } finally {
    statusButtons.forEach(function (button) { button.disabled = false; });
  }
}

/** 既存のアバター選択モーダルを部屋画面から開きます。 */
function openAvatarModal() {
  showAvatarModal();
}

/** URLに対応する部屋を初期表示します。 */
async function initializeRoom(queryString) {
  currentRoomInfo = getRoomInfo(queryString);
  connectRoomWebSocket(currentRoomInfo.roomId);
  myStatus = "working";
  roomEnteredAt = Date.now();
  loadMyWorkspaceState();
  selectedChatTarget = "all";
  switchSideTab("self");
  updateElapsedTime();
  setMyStatus(myStatus);
  currentParticipants = [];
  renderWorkspace(currentRoomInfo, currentParticipants);
  try {
    currentParticipants = await loadRoomParticipants(currentRoomInfo.roomId);
    const myParticipant = currentParticipants.find(function (participant) {
      return participant.isMe;
    });
    if (myParticipant) setMyStatus(myParticipant.status);
    renderWorkspace(currentRoomInfo, currentParticipants);
  } catch (error) {
    currentParticipants = [];
    renderWorkspace(currentRoomInfo, currentParticipants);
    console.error(error);
  }
}

changeAvatarButton.addEventListener("click", openAvatarModal);
changeThemeButton.addEventListener("click", openRoomSettingsModal);
leaveRoomButton.addEventListener("click", async function () {
  if (!currentRoomInfo?.roomId) return;

  leaveRoomError.textContent = "";
  leaveRoomButton.disabled = true;
  try {
    const response = await fetch(
      `/api/rooms/${encodeURIComponent(currentRoomInfo.roomId)}/seat-assignments/me`,
      { method: "DELETE", credentials: "same-origin" }
    );
    if (response.status === 401) {
      clearCurrentPrivateRoom();
      clearAuthenticatedUser();
      showView("login");
      return;
    }
    if (!response.ok) {
      const responseBody = await response.json().catch(function () { return null; });
      throw new Error(responseBody?.message || "退席処理に失敗しました");
    }

    clearCurrentPrivateRoom();
    disconnectRoomWebSocket();
    currentRoomInfo = null;
    currentParticipants = [];
    showView("lobby");
  } catch (error) {
    leaveRoomError.textContent = error.message;
  } finally {
    leaveRoomButton.disabled = false;
  }
});

statusButtons.forEach(function (button) {
  button.addEventListener("click", async function () {
    await updateMyStatus(button.dataset.status);
  });
});

sideTabs.forEach(function (tab) {
  tab.addEventListener("click", function () {
    switchSideTab(tab.dataset.sideTab);
  });
});

myWorkContentInput.addEventListener("input", function () {
  saveWorkContent(myWorkContentInput.value);
  if (currentRoomInfo) renderParticipants(currentParticipants);
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
  if (!currentRoomInfo?.roomId) return;
  loadRoomParticipants(currentRoomInfo.roomId)
    .then(function (participants) {
      currentParticipants = participants;
      renderWorkspace(currentRoomInfo, currentParticipants);
    })
    .catch(console.error);
});

document.addEventListener("viewchange", function (event) {
  if (event.detail.view === "room") {
    initializeRoom(event.detail.query);
  } else {
    disconnectRoomWebSocket();
  }
});

// main.html#room?...を直接開いた場合にも部屋情報を表示します。
if (!document.getElementById("room-view").hidden) {
  initializeRoom(getQueryFromUrl());
}

// 経過時間の表示だけを1分ごとに更新します。APIの定期ポーリングは行いません。
window.setInterval(function () {
  updateElapsedTime();
}, 60000);
