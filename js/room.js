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
const saveWorkContentButton = document.getElementById("save-work-content-button");
const workContentError = document.getElementById("work-content-error");
const privateMemoInput = document.getElementById("private-memo");
const chatTargetSelect = document.getElementById("chat-target");
const chatHistoryTitle = document.getElementById("chat-history-title");
const chatHistory = document.getElementById("chat-history");
const chatMessageInput = document.getElementById("chat-message-input");
const sendChatButton = document.getElementById("send-chat-button");
const chatError = document.getElementById("chat-error");
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
let roomSubscription = null;
let privateChatSubscription = null;
let intentionalRoomWebSocketDisconnect = true;
let roomWebSocketHasConnected = false;
let roomParticipantsRefreshInProgress = false;
let roomParticipantsRefreshRequested = false;
let roomParticipantsRefreshRoomId = null;
const HEARTBEAT_INTERVAL_MS = 15000;
let heartbeatIntervalId = null;
const heartbeatRequestsInProgress = new Set();

// 全体チャットと個別チャットの履歴は、送信先ごとに分けて管理します。
const chatHistories = {
  all: []
};
const displayedChatMessageIds = new Set();
const displayedPrivateChatMessageIds = new Map();

/** 現在のユーザー名を取得します。 */
function getWorkspaceUsername() {
  return getAuthenticatedUser()?.username || "ゲスト";
}

/** ユーザーごとに保存された作業内容と個人メモを復元します。 */
function loadMyWorkspaceState() {
  const username = getWorkspaceUsername();
  myWorkContent = "";
  privateMemo = localStorage.getItem(`privateMemo:${username}`) || "";
  sideUsername.textContent = username;
  myWorkContentInput.value = myWorkContent;
  privateMemoInput.value = privateMemo;
}

/** 他の利用者にも見える作業内容を仮保存します。 */
async function saveWorkContent() {
  if (!currentRoomInfo?.roomId) return;
  workContentError.textContent = "";
  saveWorkContentButton.disabled = true;
  try {
    const response = await fetch(
      `/api/rooms/${encodeURIComponent(currentRoomInfo.roomId)}/seat-assignments/me/work-content`,
      {
        method: "PATCH",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ workContent: myWorkContentInput.value })
      }
    );
    const participant = await readRoomApi(response, "作業内容を保存できませんでした");
    myWorkContent = participant.workContent || "";
    myWorkContentInput.value = myWorkContent;
  } catch (error) {
    workContentError.textContent = error.message;
  } finally {
    saveWorkContentButton.disabled = false;
  }
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
  workspaceScene.dataset.theme = theme;
  roomBackgroundLayer.src = roomBackgrounds[theme];
  roomBackgroundLayer.alt = `${currentRoomInfo.roomName}の背景`;
  const hideChairLayers = theme === "focus";
  upperChairLayer.hidden = hideChairLayers;
  lowerChairLayer.hidden = hideChairLayers;
  const privateRoom = getCurrentPrivateRoom();
  if (privateRoom) {
    privateRoom.theme = theme;
    saveCurrentPrivateRoom(privateRoom);
  }
}

/** 作成者が選んだテーマをAPIへ保存します。画面反映はWebSocket通知で行います。 */
async function saveRoomTheme() {
  const room = getCurrentPrivateRoom();
  if (!room || !isCurrentUserRoomCreator(room) || !selectedRoomTheme) return;

  saveRoomThemeButton.disabled = true;
  try {
    const response = await fetch(`/api/rooms/${encodeURIComponent(room.roomId)}/theme`, {
      method: "PATCH",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ theme: selectedRoomTheme })
    });
    const updatedRoom = await readRoomApi(response, "テーマを保存できませんでした");
    saveCurrentPrivateRoom({ ...room, ...updatedRoom, isCreator: true });
    closeRoomSettingsModal();
  } catch (error) {
    window.alert(error.message);
  } finally {
    saveRoomThemeButton.disabled = false;
  }
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

async function loadRoomDetails(roomId) {
  const response = await fetch(
    `/api/rooms/${encodeURIComponent(roomId)}`,
    { credentials: "same-origin" }
  );
  return readRoomApi(response, "部屋情報を取得できませんでした");
}

function synchronizeCurrentRoomDetails(room) {
  const loginUserId = Number(getAuthenticatedUser()?.userId);
  currentRoomInfo = {
    ...currentRoomInfo,
    roomId: room.roomId,
    roomCode: room.roomCode,
    roomName: room.roomName,
    theme: room.theme,
    isPrivate: room.roomType === "private",
    isCreator: Number(room.createdBy) === loginUserId
  };
  if (currentRoomInfo.isPrivate) saveCurrentPrivateRoom(currentRoomInfo);
}

/** 保存済みの全体チャット最新50件をREST APIから取得します。 */
async function loadRoomChatHistory(roomId) {
  if (!roomId) return [];
  const response = await fetch(
    `/api/rooms/${encodeURIComponent(roomId)}/chat-messages`,
    { credentials: "same-origin" }
  );
  return readRoomApi(response, "チャット履歴を取得できませんでした");
}

async function loadPrivateChatHistory(roomId, otherUserId) {
  const response = await fetch(
    `/api/rooms/${encodeURIComponent(roomId)}/chat-messages/private/${encodeURIComponent(otherUserId)}`,
    { credentials: "same-origin" }
  );
  return readRoomApi(response, "個別チャット履歴を取得できませんでした");
}

/** 通知された部屋の参加者をRESTから再取得し、既存の描画処理へ反映します。 */
async function refreshRoomParticipants(roomId) {
  roomParticipantsRefreshRoomId = roomId;
  if (roomParticipantsRefreshInProgress) {
    roomParticipantsRefreshRequested = true;
    return;
  }

  roomParticipantsRefreshInProgress = true;
  try {
    do {
      roomParticipantsRefreshRequested = false;
      const refreshRoomId = roomParticipantsRefreshRoomId;
      if (Number(currentRoomInfo?.roomId) !== refreshRoomId) continue;

      try {
        const participants = await loadRoomParticipants(refreshRoomId);
        if (Number(currentRoomInfo?.roomId) !== refreshRoomId) continue;
        currentParticipants = participants;
        renderWorkspace(currentRoomInfo, currentParticipants);
      } catch (error) {
        console.error("参加者一覧のリアルタイム更新に失敗しました", error);
      }
    } while (roomParticipantsRefreshRequested);
  } finally {
    roomParticipantsRefreshInProgress = false;
  }
}

/** 現在表示中の部屋に対する参加者変更通知だけを処理します。 */
function handleRoomRealtimeEvent(event) {
  const eventRoomId = Number(event?.roomId);
  if (!Number.isInteger(eventRoomId)
      || eventRoomId !== Number(currentRoomInfo?.roomId)) return;

  if (event?.type === "chat-message") {
    appendRoomChatMessage(event);
    return;
  }
  if (event?.type === "theme-changed") {
    applyRoomTheme(event.theme);
    return;
  }
  if (event?.type !== "participants-changed"
      && event?.type !== "status-changed"
      && event?.type !== "work-content-changed"
      && event?.type !== "avatar-changed") return;

  refreshRoomParticipants(eventRoomId);
}

/** 現在の部屋に着席中であることをサーバーへ通知します。 */
async function sendHeartbeat(roomId) {
  if (heartbeatRequestsInProgress.has(roomId)
      || Number(currentRoomInfo?.roomId) !== roomId) return;
  heartbeatRequestsInProgress.add(roomId);
  try {
    const response = await fetch(`/api/rooms/${encodeURIComponent(roomId)}/heartbeat`, {
      method: "POST",
      credentials: "same-origin"
    });
    if (response.status === 401) {
      stopHeartbeat();
      clearCurrentPrivateRoom();
      clearAuthenticatedUser();
      showView("login");
      return;
    }
    if (!response.ok) {
      console.warn("heartbeatの更新に失敗しました", response.status);
    }
  } catch (error) {
    console.warn("heartbeatの送信に失敗しました", error);
  } finally {
    heartbeatRequestsInProgress.delete(roomId);
  }
}

function stopHeartbeat() {
  if (heartbeatIntervalId !== null) {
    window.clearInterval(heartbeatIntervalId);
    heartbeatIntervalId = null;
  }
}

function startHeartbeat(roomId) {
  stopHeartbeat();
  const normalizedRoomId = Number(roomId);
  if (!Number.isInteger(normalizedRoomId) || normalizedRoomId <= 0) return;
  sendHeartbeat(normalizedRoomId);
  heartbeatIntervalId = window.setInterval(function () {
    sendHeartbeat(normalizedRoomId);
  }, HEARTBEAT_INTERVAL_MS);
}

/** 再接続後にDBの現在状態を取得し、timeout済みならロビーへ戻します。 */
async function synchronizeRoomAfterWebSocketConnect(client, roomId) {
  try {
    const [participants, room] = await Promise.all([
      loadRoomParticipants(roomId),
      loadRoomDetails(roomId)
    ]);
    if (roomStompClient !== client
        || Number(currentRoomInfo?.roomId) !== roomId) return;

    const loginUserId = Number(getAuthenticatedUser()?.userId);
    const canIdentifyCurrentUser = Number.isInteger(loginUserId) && loginUserId > 0;
    const currentUserIsSeated = participants.some(function (participant) {
      return participant.isMe;
    });

    if (canIdentifyCurrentUser && !currentUserIsSeated) {
      stopHeartbeat();
      disconnectRoomWebSocket();
      clearCurrentPrivateRoom();
      currentRoomInfo = null;
      currentParticipants = [];
      window.alert("接続が長時間途切れたため退席しました");
      showView("lobby");
      return;
    }

    synchronizeCurrentRoomDetails(room);
    currentParticipants = participants;
    const myParticipant = participants.find(function (participant) { return participant.isMe; });
    if (myParticipant) {
      myWorkContent = myParticipant.memo || "";
      myWorkContentInput.value = myWorkContent;
    }
    renderWorkspace(currentRoomInfo, currentParticipants);
    const history = await loadRoomChatHistory(roomId);
    if (roomStompClient !== client
        || Number(currentRoomInfo?.roomId) !== roomId) return;
    mergeRoomChatMessages("all", history);
    if (selectedChatTarget !== "all") {
      const privateHistory = await loadPrivateChatHistory(roomId, selectedChatTarget);
      if (roomStompClient === client
          && Number(currentRoomInfo?.roomId) === roomId) {
        mergeRoomChatMessages(selectedChatTarget, privateHistory);
      }
    }
    console.log("Room state refreshed");
  } catch (error) {
    console.warn("WebSocket再接続後の参加者同期に失敗しました", error);
  }
}

/** SPAで残った購読と接続を終了します。座席の退席処理は行いません。 */
function disconnectRoomWebSocket() {
  intentionalRoomWebSocketDisconnect = true;
  roomWebSocketHasConnected = false;
  if (roomSubscription) {
    try {
      roomSubscription.unsubscribe();
    } catch (error) {
      console.error("WebSocketの購読解除に失敗しました", error);
    }
    roomSubscription = null;
  }
  if (privateChatSubscription) {
    try {
      privateChatSubscription.unsubscribe();
    } catch (error) {
      console.error("個別チャットの購読解除に失敗しました", error);
    }
    privateChatSubscription = null;
  }
  if (!roomStompClient) return;
  const client = roomStompClient;
  roomStompClient = null;
  client.deactivate().catch(function (error) {
    console.error("WebSocketの切断に失敗しました", error);
  });
}

/** 指定した部屋だけのSTOMP topicを購読します。 */
function connectRoomWebSocket(roomId) {
  disconnectRoomWebSocket();
  const normalizedRoomId = Number(roomId);
  if (!Number.isInteger(normalizedRoomId) || normalizedRoomId <= 0) {
    console.error("roomIdが不正なためWebSocketへ接続しません", roomId);
    return;
  }
  if (!window.StompJs?.Client) {
    console.error("STOMPクライアントを読み込めなかったため、リアルタイム更新を開始できません");
    return;
  }

  const socketProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  intentionalRoomWebSocketDisconnect = false;
  roomWebSocketHasConnected = false;
  const client = new window.StompJs.Client({
    brokerURL: `${socketProtocol}//${window.location.host}/ws`,
    reconnectDelay: 1000,
    reconnectTimeMode: window.StompJs.ReconnectionTimeMode?.EXPONENTIAL,
    maxReconnectDelay: 30000,
    onConnect: function () {
      if (roomStompClient !== client
          || intentionalRoomWebSocketDisconnect) return;
      const activeRoomId = Number(currentRoomInfo?.roomId);
      if (!Number.isInteger(activeRoomId) || activeRoomId <= 0) return;

      if (roomSubscription) {
        try {
          roomSubscription.unsubscribe();
        } catch (error) {
          console.warn("古いWebSocket購読の解除に失敗しました", error);
        }
        roomSubscription = null;
      }
      if (privateChatSubscription) {
        try {
          privateChatSubscription.unsubscribe();
        } catch (error) {
          console.warn("古い個別チャット購読の解除に失敗しました", error);
        }
        privateChatSubscription = null;
      }

      console.log(roomWebSocketHasConnected
        ? "WebSocket reconnected"
        : "WebSocket connected");
      const destination = `/topic/room/${activeRoomId}`;
      roomSubscription = client.subscribe(destination, function (message) {
        try {
          const event = JSON.parse(message.body);
          console.log("Room realtime event:", event);
          handleRoomRealtimeEvent(event);
        } catch (error) {
          console.error("WebSocket通知の解析に失敗しました", error);
        }
      });
      privateChatSubscription = client.subscribe(
        "/user/queue/private-chat",
        function (message) {
          try {
            handlePrivateChatMessage(JSON.parse(message.body));
          } catch (error) {
            console.error("個別チャット通知の解析に失敗しました", error);
          }
        }
      );
      console.log(roomWebSocketHasConnected
        ? `Resubscribed to ${destination}`
        : `Subscribed to ${destination}`);
      roomWebSocketHasConnected = true;
      synchronizeRoomAfterWebSocketConnect(client, activeRoomId);
    },
    onStompError: function (frame) {
      console.error("WebSocketのSTOMPエラー", frame.headers.message);
    },
    onWebSocketError: function (error) {
      if (roomStompClient === client && !intentionalRoomWebSocketDisconnect) {
        console.warn("WebSocket接続に失敗しました。自動再接続を待機します", error);
      }
    },
    onWebSocketClose: function () {
      if (roomStompClient === client && !intentionalRoomWebSocketDisconnect) {
        roomSubscription = null;
        console.warn("WebSocket disconnected. Reconnecting WebSocket...");
      }
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
  const memoLabel = participant.memo || "未設定";
  const lines = [
    { text: `${participant.name}さん`, strong: true },
    { text: `状態: ${statusLabel}` },
    { text: `経過: ${participant.elapsedTime}` },
    { text: `作業内容: ${memoLabel}` }
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

/** 全体と、現在同じ部屋にいる自分以外の参加者を送信先にします。 */
function populateChatTargets(participants) {
  chatTargetSelect.replaceChildren();
  const allOption = document.createElement("option");
  allOption.value = "all";
  allOption.textContent = "全体";
  chatTargetSelect.appendChild(allOption);
  participants.filter(function (participant) { return !participant.isMe; })
    .forEach(function (participant) {
      const option = document.createElement("option");
      option.value = participant.id;
      option.textContent = `${participant.name}さん`;
      chatTargetSelect.appendChild(option);
      if (!chatHistories[participant.id]) chatHistories[participant.id] = [];
      if (!displayedPrivateChatMessageIds.has(participant.id)) {
        displayedPrivateChatMessageIds.set(participant.id, new Set());
      }
    });
  const selectedTargetExists = [...chatTargetSelect.options].some(function (option) {
    return option.value === selectedChatTarget;
  });
  if (!selectedTargetExists) {
    selectedChatTarget = "all";
    renderChatHistory();
    updateChatPlaceholder();
  }
  chatTargetSelect.value = selectedChatTarget;
  chatTargetSelect.disabled = false;
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
async function selectChatTarget(userId) {
  if (userId === "me") return;
  const targetExists = userId === "all" || Boolean(getChatTargetParticipant(userId));
  if (!targetExists) return;

  selectedChatTarget = userId;
  chatTargetSelect.value = userId;
  switchSideTab("chat");
  renderChatHistory();
  updateChatPlaceholder();
  scrollChatToBottom();
  if (userId === "all" || !currentRoomInfo?.roomId) return;
  try {
    const history = await loadPrivateChatHistory(currentRoomInfo.roomId, userId);
    if (selectedChatTarget === userId) mergeRoomChatMessages(userId, history);
  } catch (error) {
    chatError.textContent = error.message;
  }
}

/** 指定された履歴へメッセージを追加します。 */
function addChatMessage(targetId, message) {
  if (!chatHistories[targetId]) chatHistories[targetId] = [];
  chatHistories[targetId].push(message);
}

/** chat-messageイベントをプレーンテキストとして全体チャットへ追加します。 */
function appendRoomChatMessage(message) {
  mergeRoomChatMessages("all", [message]);
}

function handlePrivateChatMessage(message) {
  if (message?.type !== "private-chat-message"
      || Number(message.roomId) !== Number(currentRoomInfo?.roomId)) return;
  const myUserId = Number(getAuthenticatedUser()?.userId);
  const senderUserId = Number(message.userId);
  const targetUserId = Number(message.targetUserId);
  const otherUserId = senderUserId === myUserId ? targetUserId : senderUserId;
  if (!Number.isInteger(otherUserId)) return;
  mergeRoomChatMessages(String(otherUserId), [message]);
}

/** REST履歴とWebSocket新着をmessageIdで重複排除し、時系列順に表示します。 */
function mergeRoomChatMessages(targetId, messages) {
  const messageIds = targetId === "all"
    ? displayedChatMessageIds
    : displayedPrivateChatMessageIds.get(targetId) || new Set();
  if (targetId !== "all" && !displayedPrivateChatMessageIds.has(targetId)) {
    displayedPrivateChatMessageIds.set(targetId, messageIds);
  }
  let changed = false;
  messages.forEach(function (message) {
    const messageId = Number(message?.messageId);
    if (!Number.isInteger(messageId)
        || messageIds.has(messageId)
        || typeof message?.username !== "string"
        || typeof message?.content !== "string") return;
    messageIds.add(messageId);
    addChatMessage(targetId, {
      messageId,
      senderId: String(message.userId),
      senderName: message.username,
      content: message.content,
      sentAt: message.sentAt
    });
    changed = true;
  });
  if (!changed) return;
  chatHistories[targetId].sort(function (first, second) {
    const timeDifference = Date.parse(first.sentAt) - Date.parse(second.sentAt);
    return timeDifference || first.messageId - second.messageId;
  });
  if (selectedChatTarget === targetId) {
    renderChatHistory();
    scrollChatToBottom();
  }
}

/** Enterまたは送信ボタンから呼び出す共通送信処理です。 */
function sendChatMessage() {
  const content = chatMessageInput.value.trim();
  chatError.textContent = "";
  if (!content || content.length > 500) {
    chatError.textContent = "メッセージは1文字以上500文字以内で入力してください";
    return;
  }
  if (!roomStompClient?.connected || !currentRoomInfo?.roomId) {
    chatError.textContent = "WebSocketの再接続後にもう一度送信してください";
    return;
  }

  try {
    roomStompClient.publish({
      destination: `/app/room/${currentRoomInfo.roomId}/chat`,
      body: JSON.stringify({
        targetUserId: selectedChatTarget === "all" ? null : Number(selectedChatTarget),
        content
      })
    });
    chatMessageInput.value = "";
  } catch (error) {
    chatError.textContent = "メッセージを送信できませんでした";
    console.warn("チャット送信に失敗しました", error);
  }
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
  stopHeartbeat();
  disconnectRoomWebSocket();
  currentRoomInfo = getRoomInfo(queryString);
  myStatus = "working";
  roomEnteredAt = Date.now();
  loadMyWorkspaceState();
  selectedChatTarget = "all";
  Object.keys(chatHistories).forEach(function (targetId) {
    if (targetId !== "all") delete chatHistories[targetId];
  });
  chatHistories.all = [];
  displayedChatMessageIds.clear();
  displayedPrivateChatMessageIds.clear();
  chatError.textContent = "";
  switchSideTab("self");
  updateElapsedTime();
  setMyStatus(myStatus);
  currentParticipants = [];
  renderWorkspace(currentRoomInfo, currentParticipants);
  try {
    const [participants, room] = await Promise.all([
      loadRoomParticipants(currentRoomInfo.roomId),
      loadRoomDetails(currentRoomInfo.roomId)
    ]);
    synchronizeCurrentRoomDetails(room);
    currentParticipants = participants;
    const history = await loadRoomChatHistory(currentRoomInfo.roomId);
    const myParticipant = currentParticipants.find(function (participant) {
      return participant.isMe;
    });
    if (myParticipant) {
      setMyStatus(myParticipant.status);
      myWorkContent = myParticipant.memo || "";
      myWorkContentInput.value = myWorkContent;
    }
    renderWorkspace(currentRoomInfo, currentParticipants);
    mergeRoomChatMessages("all", history);
  } catch (error) {
    currentParticipants = [];
    renderWorkspace(currentRoomInfo, currentParticipants);
    console.error(error);
  }
  if (document.getElementById("room-view").hidden) return;
  connectRoomWebSocket(currentRoomInfo.roomId);
  startHeartbeat(currentRoomInfo.roomId);
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
    stopHeartbeat();
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

saveWorkContentButton.addEventListener("click", saveWorkContent);

myWorkContentInput.addEventListener("keydown", function (event) {
  if (event.key === "Enter" && !event.isComposing) {
    event.preventDefault();
    saveWorkContent();
  }
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
    stopHeartbeat();
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
