#!/bin/bash
# ============================================
# MockHub 服务管理脚本
# 用法: ./mockhub.sh {start|stop|restart|status}
# ============================================

APP_NAME="MockHub"
JAR_NAME="mockhub-1.0.0.jar"
APP_PORT=8080
PID_FILE="mockhub.pid"

# 切换到脚本所在目录
cd "$(dirname "$0")"

# 检查 jar 是否存在
check_jar() {
    if [ ! -f "$JAR_NAME" ]; then
        echo "[错误] 未找到 $JAR_NAME，请确认文件在当前目录"
        exit 1
    fi
}

# 获取 PID
get_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return
        fi
        rm -f "$PID_FILE"
    fi
}

# 启动
start() {
    check_jar
    local pid=$(get_pid)
    if [ -n "$pid" ]; then
        echo "[$APP_NAME] 已在运行，PID=$pid"
        return
    fi

    echo "[$APP_NAME] 正在启动..."
    mkdir -p logs data

    nohup java -jar "$JAR_NAME" \
        --server.port=$APP_PORT \
        --data.path=./data \
        --logging.file.path=./logs \
        > logs/console.log 2>&1 &

    echo $! > "$PID_FILE"

    # 等待启动
    for i in $(seq 1 15); do
        sleep 2
        if curl -s "http://localhost:$APP_PORT/api/health" > /dev/null 2>&1; then
            echo "[$APP_NAME] 启动成功！PID=$(cat $PID_FILE)"
            echo "[$APP_NAME] 访问地址: http://localhost:$APP_PORT"
            echo "[$APP_NAME] 数据目录: $(pwd)/data"
            echo "[$APP_NAME] 日志目录: $(pwd)/logs"
            return
        fi
    done
    echo "[$APP_NAME] 启动超时，请检查 logs/console.log"
}

# 停止
stop() {
    local pid=$(get_pid)
    if [ -z "$pid" ]; then
        echo "[$APP_NAME] 未在运行"
        return
    fi

    echo "[$APP_NAME] 正在停止 PID=$pid..."
    kill "$pid" 2>/dev/null
    sleep 2

    if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null
        sleep 1
    fi

    rm -f "$PID_FILE"
    echo "[$APP_NAME] 已停止"
}

# 重启
restart() {
    stop
    sleep 1
    start
}

# 状态
status() {
    local pid=$(get_pid)
    if [ -n "$pid" ]; then
        echo "[$APP_NAME] 运行中，PID=$pid，端口=$APP_PORT"
    else
        echo "[$APP_NAME] 未运行"
    fi
}

# 交互式菜单
menu() {
    echo ""
    echo " ============================="
    echo "  $APP_NAME 服务管理"
    echo " ============================="
    echo "  1. 启动服务"
    echo "  2. 停止服务"
    echo "  3. 重启服务"
    echo "  4. 查看状态"
    echo "  0. 退出"
    echo " ============================="
    echo ""
    read -p "请选择操作 [0-4]: " choice
    case "$choice" in
        1) start ;;
        2) stop ;;
        3) restart ;;
        4) status ;;
        0) exit 0 ;;
        *) echo "[错误] 无效选择"; menu ;;
    esac
}

# 入口
case "${1}" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    "")      menu ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
