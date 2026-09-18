package com.agent.hopaw.tool.gomoku;

import com.agent.hopaw.infra.service.IPluginResultStore;
import com.agent.hopaw.infra.service.IWebSocketBridgeService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 五子棋工具插件后端门面。
 *
 * <p>让 LLM 与用户在浏览器前端对弈五子棋。后端负责权威的棋盘状态维护、落子合法性校验与
 * 五连胜负判定；前端负责棋盘渲染与采集用户落子。</p>
 *
 * <p>对弈闭环（复用画布插件的 WS 下行 + 暂存服务回传机制）：</p>
 * <ol>
 *   <li>{@code startGame} —— 创建对局（LLM 执黑先手），WS 下发 start 打开前端棋盘；</li>
 *   <li>{@code placePiece} —— LLM 落子，后端校验/更新/判胜负，WS 下发 place 同步到前端；</li>
 *   <li>{@code waitUserMove} —— 阻塞等待用户在前端点击落子，前端经暂存服务回传坐标，后端更新/判胜负；</li>
 *   <li>交替 {@code placePiece} / {@code waitUserMove}，直至分出胜负或和棋；</li>
 *   <li>{@code closeGame} —— 结束对局，前端关闭棋盘。</li>
 * </ol>
 *
 * <p>对局隔离：以 gameId 为键；@Tool 拿不到 userId，故各方法接受可选 gameId 参数，
 * 为空时回退到最近一次创建的对局（单会话通常仅一局活跃）。</p>
 */
public class GomokuTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(GomokuTool.class);

    private static final String TOOL_NAME = "gomoku";

    /** 默认棋盘边长。 */
    private static final int DEFAULT_BOARD_SIZE = 15;

    /** 棋盘边长最小/最大值。 */
    private static final int MIN_BOARD_SIZE = 9;
    private static final int MAX_BOARD_SIZE = 19;

    /** 等待用户落子的默认超时（秒）。倒计时同步给前端，需与 await 保持一致。 */
    private static final long USER_MOVE_TIMEOUT_SECONDS = 300L;

    @Autowired
    private IWebSocketBridgeService webSocketBridgeService;

    @Autowired
    private IPluginResultStore pluginResultStore;

    /** 对局表：gameId → Game。 */
    private final Map<String, Game> games = new ConcurrentHashMap<>();

    /** sessionId → 当前等待用户落子的 requestId（LLM 落子时提前生成，按会话隔离）。 */
    private final Map<String, String> sessionMoveRequestMap = new ConcurrentHashMap<>();

    /** 最近一次创建的对局 id（供 gameId 缺省回退）。 */
    private volatile String lastGameId;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "五子棋对弈工具：LLM 与用户在浏览器前端对弈五子棋，支持开局、落子、等待用户落子、查棋盘、结束对局";
    }

    @Override
    public String getKeyword() {
        return "五子棋,gomoku,棋,对弈,下棋";
    }

    @Override
    public String getIcon() {
        return "agent-tool.svg";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // =====================================================================
    // @Tool 方法
    // =====================================================================

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "gomoku_startGame", value = {"开始五子棋对局", "在浏览器前端打开五子棋棋盘开始对局，你执黑先手，返回棋盘与规则"})
    public String startGame(
            @P(value = "棋盘边长(9-19)，默认15，值越小对局越快", required = false) Integer boardSize) {
        int size = normalizeSize(boardSize);
        String gameId = UUID.randomUUID().toString();
        Game game = new Game(size);
        games.put(gameId, game);
        lastGameId = gameId;

        sendCommand("start", gameId, null, buildStartPayload(size));
        return "五子棋对局已开始（gameId=" + gameId + "），棋盘 " + size + "x" + size
                + "，你执黑(X)先手，用户执白(O)。\n"
                + "规则：横/竖/斜任意方向连成 5 子即胜。\n"
                + "请先用 gomoku_placePiece(x, y) 落下你的第一枚黑子（坐标从 0 开始，范围 0~" + (size - 1) + "）。\n\n"
                + renderBoard(game);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "gomoku_placePiece", value = {"五子棋落子", "你在指定坐标落下一枚黑子，返回落子后的棋盘与是否分出胜负"})
    public String placePiece(
            @P("横坐标(列)，从0开始") Integer x,
            @P("纵坐标(行)，从0开始") Integer y,
            @P(value = "对局ID，不传则操作最近一局", required = false) String gameId,
            InvocationParameters invocationParameters) {
        Game game = resolveGame(gameId);
        if (game == null) {
            return "错误: 未找到进行中的对局，请先调用 gomoku_startGame 开局。";
        }
        if (!game.playing()) {
            return "对局已结束（" + game.statusText() + "），如需再来一局请调用 gomoku_startGame。";
        }
        if (x == null || y == null) {
            return "错误: 落子坐标 x、y 不能为空。";
        }
        if (x < 0 || x >= game.size || y < 0 || y >= game.size) {
            return "错误: 坐标越界，范围应为 0~" + (game.size - 1) + "。";
        }
        if (game.board[y][x] != 0) {
            return "错误: (" + x + "," + y + ") 已有棋子，请选择空位。";
        }

        game.place(x, y, Game.PIECE_LLM);

        String pendingRequestId = null;
        if (!game.finished()) {
            // LLM 落子后轮到用户：提前生成 requestId 并注册槽位，下发到前端，
            // 用户落子时直接提交该 requestId；waitUserMove 复用同一 requestId 等待。
            pendingRequestId = UUID.randomUUID().toString();
            pluginResultStore.register(pendingRequestId, userIdOf(invocationParameters));
            sessionMoveRequestMap.put(sessionKeyOf(invocationParameters), pendingRequestId);
        }

        sendCommand("place", game.getId(), pendingRequestId, buildPlacePayload(x, y, Game.PIECE_LLM, game, pendingRequestId));

        if (game.finished()) {
            return game.statusText() + "\n\n" + renderBoard(game);
        }
        return "你已在 (" + x + "," + y + ") 落子，轮到用户，请调用 gomoku_waitUserMove 等待用户落子。\n\n"
                + renderBoard(game);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "gomoku_waitUserMove", value = {"等待用户落子", "阻塞等待用户在棋盘上点击落子，返回用户落子位置与落子后的棋盘状态"})
    public String waitUserMove(
            @P(value = "对局ID，不传则操作最近一局", required = false) String gameId,
            InvocationParameters invocationParameters) {
        Game game = resolveGame(gameId);
        if (game == null) {
            return "错误: 未找到进行中的对局，请先调用 gomoku_startGame 开局。";
        }
        if (!game.playing()) {
            return "对局已结束（" + game.statusText() + "），如需再来一局请调用 gomoku_startGame。";
        }

        // 优先复用 LLM 落子（placePiece）时提前生成的 requestId；若缺失（如直接调用）则兜底新建
        String sessionKey = sessionKeyOf(invocationParameters);
        String requestId = sessionMoveRequestMap.remove(sessionKey);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            pluginResultStore.register(requestId, userIdOf(invocationParameters));
            // 兜底下发 request-move 指令，让前端进入等待状态
            Map<String, Object> waitPayload = new HashMap<>();
            waitPayload.put("timeout", USER_MOVE_TIMEOUT_SECONDS);
            sendCommand("request-move", game.getId(), requestId, waitPayload);
        }

        // 阻塞等待前端回传用户落子坐标（形如 "x,y"）
        String move = pluginResultStore.await(requestId, USER_MOVE_TIMEOUT_SECONDS);
        if (move == null || move.trim().isEmpty()) {
            return "等待用户落子超时，用户可能已离开或前端棋盘未打开。可再次调用 gomoku_waitUserMove 继续等待，或调用 gomoku_closeGame 结束。";
        }

        int[] coord = parseCoord(move);
        if (coord == null) {
            return "错误: 前端回传的落子坐标非法（" + move + "）。";
        }
        int x = coord[0], y = coord[1];
        if (x < 0 || x >= game.size || y < 0 || y >= game.size) {
            return "错误: 用户落子坐标越界（" + move + "）。";
        }
        if (game.board[y][x] != 0) {
            return "错误: 用户落子位置已被占用（" + move + "），请再次调用 gomoku_waitUserMove 等待有效落子。";
        }

        game.place(x, y, Game.PIECE_USER);

        if (game.finished()) {
            return game.statusText() + "\n\n" + renderBoard(game);
        }
        return "用户已在 (" + x + "," + y + ") 落子，轮到你，请调用 gomoku_placePiece 落子。\n\n"
                + renderBoard(game);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "gomoku_getBoard", value = {"查看五子棋棋盘", "返回当前棋盘状态，用于需要回顾棋局时"})
    public String getBoard(
            @P(value = "对局ID，不传则操作最近一局", required = false) String gameId) {
        Game game = resolveGame(gameId);
        if (game == null) {
            return "错误: 未找到进行中的对局，请先调用 gomoku_startGame 开局。";
        }
        return renderBoard(game);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "gomoku_closeGame", value = {"结束五子棋对局", "结束当前对局并关闭前端棋盘"})
    public String closeGame(
            @P(value = "对局ID，不传则操作最近一局", required = false) String gameId) {
        Game game = resolveGame(gameId);
        if (game == null) {
            return "当前无进行中的对局。";
        }
        games.remove(game.getId());
        if (game.getId().equals(lastGameId)) {
            lastGameId = null;
        }
        sendCommand("close", game.getId(), null, null);
        return "五子棋对局已结束，棋盘已关闭。";
    }

    // =====================================================================
    // 内部逻辑
    // =====================================================================

    private int normalizeSize(Integer boardSize) {
        if (boardSize == null) {
            return DEFAULT_BOARD_SIZE;
        }
        return Math.max(MIN_BOARD_SIZE, Math.min(MAX_BOARD_SIZE, boardSize));
    }

    private Game resolveGame(String gameId) {
        if (gameId != null && !gameId.isEmpty()) {
            return games.get(gameId);
        }
        if (lastGameId != null) {
            return games.get(lastGameId);
        }
        return null;
    }

    /** 解析 "x,y" 为 int[]{x,y}，非法返回 null。 */
    private int[] parseCoord(String s) {
        try {
            String[] parts = s.trim().split(",");
            if (parts.length != 2) {
                return null;
            }
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return null;
        }
    }

    /** 构建带坐标标注的棋盘字符串，供 LLM 阅读决策。 */
    private String renderBoard(Game game) {
        int n = game.size;
        StringBuilder sb = new StringBuilder();
        sb.append("棋盘 ").append(n).append("x").append(n)
                .append("，X=你(黑)，O=用户(白)，.=空。列号(上)行号(左)从0起：\n");

        // 列号
        sb.append("   ");
        for (int c = 0; c < n; c++) {
            sb.append(colLabel(c)).append(' ');
        }
        sb.append('\n');

        for (int r = 0; r < n; r++) {
            sb.append(rowLabel(r)).append(' ');
            for (int c = 0; c < n; c++) {
                int v = game.board[r][c];
                sb.append(v == Game.PIECE_LLM ? 'X' : (v == Game.PIECE_USER ? 'O' : '.')).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 列号：0-9 直接用数字，10+ 用字母，宽度固定一位便于对齐。 */
    private String colLabel(int c) {
        if (c < 10) {
            return String.valueOf(c);
        }
        // 10 -> a, 11 -> b, ... 用单字母，最多支持到 19 -> j
        return String.valueOf((char) ('a' + (c - 10)));
    }

    /** 行号同上。 */
    private String rowLabel(int r) {
        if (r < 10) {
            return " " + r;
        }
        return " " + (char) ('a' + (r - 10));
    }

    private Map<String, Object> buildStartPayload(int size) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("size", size);
        return payload;
    }

    private Map<String, Object> buildPlacePayload(int x, int y, int piece, Game game, String pendingRequestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("x", x);
        payload.put("y", y);
        payload.put("piece", piece);
        payload.put("status", game.status);
        if (pendingRequestId != null) {
            payload.put("pendingRequestId", pendingRequestId);
            payload.put("timeout", USER_MOVE_TIMEOUT_SECONDS);
        }
        return payload;
    }

    /** 从 InvocationParameters 提取 userId（用于结果回传校验），可能为 null。 */
    private String userIdOf(InvocationParameters invocationParameters) {
        if (invocationParameters == null) {
            return null;
        }
        return InvocationParametersWrapper.create(invocationParameters).getUserId();
    }

    /** 从 InvocationParameters 提取 sessionId（会话标识，用于区分会话），可能为 null。 */
    private String sessionIdOf(InvocationParameters invocationParameters) {
        if (invocationParameters == null) {
            return null;
        }
        return InvocationParametersWrapper.create(invocationParameters).getSessionId();
    }

    /** 以 sessionId 作为缓存 key（区分会话），null 时用空串兜底。 */
    private String sessionKeyOf(InvocationParameters invocationParameters) {
        String sid = sessionIdOf(invocationParameters);
        return sid == null ? "" : sid;
    }

    /**
     * 下发指令到前端插件（通过 /ws/plugin 下行通道，userId 为空时广播）。
     * gameId 放入 cmd，前端据此识别当前对局。
     */
    private void sendCommand(String action, String gameId, String requestId, Map<String, Object> payload) {
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("toolName", TOOL_NAME);
        cmd.put("action", action);
        cmd.put("gameId", gameId);
        cmd.put("requestId", requestId);
        cmd.put("payload", payload);
        try {
            webSocketBridgeService.sendPluginCommand(null, JSON.toJSONString(cmd));
        } catch (Exception e) {
            logger.error("GomokuTool: failed to send plugin command action={}", action, e);
        }
    }

    /**
     * 一局五子棋的状态。棋盘用 int[][]：0=空，1=LLM(X)，2=用户(O)。
     */
    private static final class Game {
        static final int PIECE_LLM = 1;
        static final int PIECE_USER = 2;

        static final int STATUS_PLAYING = 0;
        static final int STATUS_LLM_WIN = 1;
        static final int STATUS_USER_WIN = 2;
        static final int STATUS_DRAW = 3;

        private final String id;
        private final int size;
        private final int[][] board;
        private int status = STATUS_PLAYING;
        private int moveCount = 0;

        Game(int size) {
            this.id = UUID.randomUUID().toString();
            this.size = size;
            this.board = new int[size][size];
        }

        String getId() {
            return id;
        }

        boolean playing() {
            return status == STATUS_PLAYING;
        }

        boolean finished() {
            return status != STATUS_PLAYING;
        }

        String statusText() {
            switch (status) {
                case STATUS_LLM_WIN:
                    return "你(X)获胜！";
                case STATUS_USER_WIN:
                    return "用户(O)获胜！";
                case STATUS_DRAW:
                    return "棋盘已满，和棋！";
                default:
                    return "对局进行中";
            }
        }

        /** 落子并更新胜负状态。 */
        void place(int x, int y, int piece) {
            board[y][x] = piece;
            moveCount++;
            if (checkWin(x, y, piece)) {
                status = (piece == PIECE_LLM) ? STATUS_LLM_WIN : STATUS_USER_WIN;
            } else if (moveCount >= size * size) {
                status = STATUS_DRAW;
            }
        }

        /** 以 (x,y) 为中心检查四个方向是否五连。 */
        private boolean checkWin(int x, int y, int piece) {
            // 方向：(dx,dy) 四组：横、竖、主对角、副对角
            int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
            for (int[] d : dirs) {
                int count = 1;
                count += countDir(x, y, d[0], d[1], piece);
                count += countDir(x, y, -d[0], -d[1], piece);
                if (count >= 5) {
                    return true;
                }
            }
            return false;
        }

        private int countDir(int x, int y, int dx, int dy, int piece) {
            int count = 0;
            int cx = x + dx, cy = y + dy;
            while (cx >= 0 && cx < size && cy >= 0 && cy < size && board[cy][cx] == piece) {
                count++;
                cx += dx;
                cy += dy;
            }
            return count;
        }
    }
}
