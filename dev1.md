- - # 后端A (核心服务) 开发进度 - Dev1
  
    
  
    本文档记录了后端核心服务（Backend-A, Dev1）的开发进度、关键说明和后续计划，旨在帮助团队其他成员快速理解当前系统状态并进行协作。
  
    
  
    ## 1. 当前进度
  
    
  
    核心服务的架构和功能已基本实现。
  
    - **[已完成]** **专业级房间/会话管理**:
      - 通过 `GameRoomManager` 和线程池实现了对多个游戏房间的稳定管理。
      - 通过 `computeIfAbsent` 解决了高并发下的**竞态条件**问题，确保房间创建的线程安全。
      - 通过引入 **HTTP接口** (`RoomController`) 和 **CORS配置**，实现了业务逻辑与核心玩法的完全解耦。
    - **[已完成]** **核心战斗循环**:
      - `GameRoom` 内已完整实现**移动、开火、受击、死亡、复活**的逻辑。
      - 通过修复**并发修改异常** (使用`Iterator`) 和实现**深拷贝** (`PlayerState`的拷贝构造函数)，解决了所有已知的系统崩溃和线程安全问题。
    - **[已完成]** **稳定的20Hz状态同步**: 服务器严格按照20Hz的频率，通过WebSocket向客户端广播权威的 `GameStateSnapshot`，数据链路稳定可靠。
  
    
  
    ## 2. 如何运行与测试
  
    
  
    由于架构升级，测试流程分为两步，完美模拟真实客户端的行为。
  
    1. **运行后端**: 直接启动 `PixelStrikeBackEndApplication`。
    2. **使用Web测试台 (`test-client.html`)**:
       - 用浏览器打开项目根目录下的 `test-client.html` 文件。
       - 点击 **“1. 快速加入 (Quick Join)”** 按钮。测试台会自动发送HTTP请求到 `/api/rooms/quick-join` 获取房间ID，然后用此ID建立WebSocket连接。
       - 连接成功后，你就可以在 **“发送指令”** 文本框中修改JSON，测试移动、开火等所有同步功能。
       - **强烈建议**: 并排打开两个浏览器窗口来模拟和测试多玩家同步。
  
    ```html
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
        <meta charset="UTF-8">
        <title>PixelStrike 后端测试台 (HTTP + WebSocket)</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; display: flex; padding: 1em; }
            .container { border: 1px solid #ccc; border-radius: 8px; padding: 1em; margin-right: 1em; width: 50%; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            h2 { border-bottom: 2px solid #eee; padding-bottom: 0.5em; }
            #log { background-color: #f7f7f7; border: 1px solid #eee; height: 400px; overflow-y: scroll; padding: 8px; white-space: pre-wrap; font-family: "SF Mono", "Fira Code", "Consolas", monospace; font-size: 0.9em; }
            textarea { width: 95%; height: 120px; padding: 5px; border-radius: 4px; border: 1px solid #ccc; }
            button { background-color: #007bff; color: white; border: none; padding: 10px 15px; border-radius: 5px; cursor: pointer; margin-right: 5px; }
            button:disabled { background-color: #ccc; cursor: not-allowed; }
            #status { font-weight: bold; }
            .highlight { color: #28a745; font-weight: bold; }
        </style>
    </head>
    <body>
        <div class="container">
            <h2>连接控制</h2>
            <button id="quickJoinBtn">1. 快速加入 (Quick Join)</button>
            <button id="disconnectBtn" disabled>2. 断开连接</button>
            <p>连接状态: <span id="status" style="color: red;">未连接</span></p>
            <p>当前房间ID: <span id="roomIdSpan">N/A</span></p>
    
            <h2>发送指令 (UserCommand)</h2>
            <textarea id="userCommandInput"></textarea>
            <button id="sendBtn" disabled>发送指令</button>
            <p><i>提示: 打开多个浏览器窗口来模拟多玩家。</i></p>
        </div>
        <div class="container">
            <h2>日志</h2>
            <div id="log"></div>
        </div>
    
    <script>
        const quickJoinBtn = document.getElementById('quickJoinBtn');
        const disconnectBtn = document.getElementById('disconnectBtn');
        const sendBtn = document.getElementById('sendBtn');
        const statusSpan = document.getElementById('status');
        const roomIdSpan = document.getElementById('roomIdSpan');
        const logDiv = document.getElementById('log');
        const commandInput = document.getElementById('userCommandInput');
    
        let socket;
        let commandSequence = 0;
    
        // 预设指令模板
        commandInput.value = JSON.stringify({
            commandSequence: 0,
            timestamp: Date.now(),
            moveInput: 1.0,
            aimAngle: 0.0,
            actions: 0
        }, null, 2);
    
        quickJoinBtn.onclick = async () => {
            log('➡️ 步骤1: 正在向 /api/rooms/quick-join 发送HTTP POST请求...');
            try {
                // 第一步：发送HTTP POST请求获取房间ID
                const response = await fetch("http://localhost:81/api/rooms/quick-join", { method: 'POST' });
                if (!response.ok) {
                    throw new Error(`HTTP请求失败, 状态: ${response.status}`);
                }
                const data = await response.json();
                const roomId = data.roomId;
                
                if (!roomId) {
                    throw new Error('从服务器返回的数据中未找到roomId');
                }
    
                log(`✅ HTTP请求成功！获取到房间ID: <span class="highlight">${roomId}</span>`);
                roomIdSpan.textContent = roomId;
    
                // 第二步：使用获取到的roomId构建WebSocket URL并连接
                const wsUrl = `ws://localhost:81/game?roomId=${roomId}`;
                log(`➡️ 步骤2: 正在连接到 WebSocket: ${wsUrl}`);
                
                socket = new WebSocket(wsUrl);
                setupSocketHandlers();
    
            } catch (error) {
                log(`❌ 错误: ${error.message}`);
                console.error(error);
            }
        };
    
        function setupSocketHandlers() {
            socket.onopen = (event) => {
                statusSpan.textContent = '已连接';
                statusSpan.style.color = 'green';
                quickJoinBtn.disabled = true;
                disconnectBtn.disabled = false;
                sendBtn.disabled = false;
                log('✅ WebSocket 连接已建立。');
            };
    
            socket.onmessage = (event) => {
                const snapshot = JSON.parse(event.data);
                log(`⬅️ 收到快照 (Tick: ${snapshot.tickNumber}):\n${JSON.stringify(snapshot, null, 2)}`);
            };
    
            socket.onclose = (event) => {
                statusSpan.textContent = '已断开';
                statusSpan.style.color = 'red';
                quickJoinBtn.disabled = false;
                disconnectBtn.disabled = true;
                sendBtn.disabled = true;
                roomIdSpan.textContent = 'N/A';
                log(`🔌 WebSocket 连接已关闭。代码: ${event.code}`);
            };
    
            socket.onerror = (error) => {
                log(`❌ WebSocket 发生错误: ${error.message || '未知错误'}`);
            };
        }
    
        disconnectBtn.onclick = () => {
            if (socket) socket.close();
        };
        
        sendBtn.onclick = () => {
            if (socket && socket.readyState === WebSocket.OPEN) {
                const command = JSON.parse(commandInput.value);
                command.commandSequence = ++commandSequence;
                command.timestamp = Date.now();
                const updatedCommandText = JSON.stringify(command, null, 2);
                commandInput.value = updatedCommandText;
                socket.send(updatedCommandText);
                log(`➡️ 发送指令:\n${updatedCommandText}`);
            } else {
                log('无法发送，WebSocket未连接。');
            }
        };
    
        function log(message) {
            logDiv.innerHTML += `<p>${message.replace(/\n/g, '<br>')}</p>`;
            logDiv.scrollTop = logDiv.scrollHeight;
        }
    </script>
    </body>
    </html>
    ```
  
    
  
    
  
    ## 3. 给其他开发者的说明
  
    
  
    
  
    ### For Dev2 (业务服务)
  
    
  
    - **接口**: `RoomController.java` 中暴露了 `POST /api/rooms/quick-join` 接口。
    - **硬编码部分**: 当前，这个接口的实现是**硬编码**的，它总是返回`"room1"`。
    - **对接**: 接管这个`quickJoin`方法，在里面实现“查找一个未满员的房间或创建一个新房间”的匹配逻辑。无论逻辑返回哪个`roomId`， `GameRoomManager` 应该都能正确处理。
  
    
  
    ### For Dev3/Dev4 (客户端 A/B)
  
    
  
    - **新的连接流程**:
      1. 客户端先向 `http://<服务器IP>:81/api/rooms/quick-join` 发送一个 **HTTP POST** 请求。
      2. 收到一个JSON响应，例如 `{"roomId": "room1"}`。
      3. 使用获取到的`roomId`，构建WebSocket的连接地址：`ws://<服务器IP>:81/game?roomId=room1`，然后建立连接。
    - **通信协议 (DTOs)**:
      - **上行 (发送给服务器)**: `UserCommand.java`。
      - **下行 (从服务器接收)**: `GameStateSnapshot.java`。这是权威状态，客户端的渲染应完全以此为准。
    - **同步频率**: 依然是 **20Hz**。请务必在客户端实现插值（Interpolation）算法以获得平滑的视觉效果。
  
    
  
    ## 4. 当前硬编码和待办事项 (TODO)
  
    
  
    - **[硬编码]** **玩家初始状态**:
      - **位置**: 在 `GameRoom.java` 的 `addPlayer` 方法中，所有玩家的初始位置被硬编码为 `(100, 100)`。未来这里应改为从地图配置中读取复活点。
      - **生命值**: 同上，在`addPlayer`中硬编码为`PLAYER_MAX_HEALTH`。
    - **[TODO - Dev1独立任务]** **完善核心玩法**:
      - **精确命中判定 (Hitscan)**: 在 `GameRoom.java` 的 `isHit` 方法中，需要用基于`aimAngle`的真实射线/几何算法替换当前简化的逻辑。
      - **弹药(Ammo)与KDA系统**: 在`PlayerState.java`中添加`kills`, `deaths`字段，并在`GameRoom.java`的开火和死亡逻辑中更新这些值。
      - **物理系统(跳跃)**: 在`GameRoom.java`的`run`方法中添加重力、跳跃初速度和地面碰撞逻辑。
    - **[TODO - 团队协作]** **协议约定**:
      - 需要与客户端同学正式约定`UserCommand`中`actions`字段的**位掩码**，例如 `1` 代表跳跃，`2` 代表开火。