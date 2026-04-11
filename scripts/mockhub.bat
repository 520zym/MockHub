@echo off
chcp 65001 >nul
setlocal

:: ============================================
:: MockHub 服务管理脚本
:: 用法: mockhub.bat [start|stop|restart|status]
:: ============================================

set APP_NAME=MockHub
set JAR_NAME=mockhub-1.4.0.jar
set APP_PORT=8080

:: 切换到脚本所在目录（确保所有文件都在 jar 同级目录）
cd /d "%~dp0"

:: 检查 jar 是否存在
if not exist "%JAR_NAME%" (
    echo [错误] 未找到 %JAR_NAME%，请确认文件在当前目录
    pause
    exit /b 1
)

:: 检查 java 是否可用
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java 环境，请安装 JDK 8 或以上版本
    pause
    exit /b 1
)

:: 默认操作：无参数时显示菜单
if "%1"=="" goto :menu
if /i "%1"=="start" goto :start
if /i "%1"=="stop" goto :stop
if /i "%1"=="restart" goto :restart
if /i "%1"=="status" goto :status
echo [错误] 未知参数: %1
echo 用法: mockhub.bat [start^|stop^|restart^|status]
exit /b 1

:: ============================================
:: 交互式菜单
:: ============================================
:menu
echo.
echo  =============================
echo   %APP_NAME% 服务管理
echo  =============================
echo   1. 启动服务
echo   2. 停止服务
echo   3. 重启服务
echo   4. 查看状态
echo   0. 退出
echo  =============================
echo.
set /p choice=请选择操作 [0-4]:
if "%choice%"=="1" goto :start
if "%choice%"=="2" goto :stop
if "%choice%"=="3" goto :restart
if "%choice%"=="4" goto :status
if "%choice%"=="0" exit /b 0
echo [错误] 无效选择
goto :menu

:: ============================================
:: 启动
:: ============================================
:start
echo.
:: 检查是否已在运行
call :find_pid
if defined PID (
    echo [%APP_NAME%] 已在运行，PID=%PID%，端口=%APP_PORT%
    goto :end
)
echo [%APP_NAME%] 正在启动...

:: 启动服务，日志输出到当前目录的 logs/mockhub.log
if not exist "logs" mkdir logs
start /b javaw -jar "%JAR_NAME%" ^
    --server.port=%APP_PORT% ^
    --data.path=./data ^
    --logging.file.path=./logs ^
    > logs\console.log 2>&1

:: 等待启动
set /a retry=0
:wait_start
timeout /t 2 /nobreak >nul
call :find_pid
if defined PID (
    echo [%APP_NAME%] 启动成功！PID=%PID%
    echo [%APP_NAME%] 访问地址: http://localhost:%APP_PORT%
    echo [%APP_NAME%] 数据目录: %cd%\data
    echo [%APP_NAME%] 日志目录: %cd%\logs
    goto :end
)
set /a retry+=1
if %retry% lss 10 goto :wait_start
echo [%APP_NAME%] 启动超时，请检查 logs\console.log
goto :end

:: ============================================
:: 停止
:: ============================================
:stop
echo.
call :find_pid
if not defined PID (
    echo [%APP_NAME%] 未在运行
    goto :end
)
echo [%APP_NAME%] 正在停止 PID=%PID%...
taskkill /PID %PID% /F >nul 2>&1
timeout /t 2 /nobreak >nul
call :find_pid
if defined PID (
    echo [%APP_NAME%] 停止失败，请手动结束进程 %PID%
) else (
    echo [%APP_NAME%] 已停止
)
goto :end

:: ============================================
:: 重启
:: ============================================
:restart
call :stop
timeout /t 2 /nobreak >nul
call :start
goto :end

:: ============================================
:: 状态
:: ============================================
:status
echo.
call :find_pid
if defined PID (
    echo [%APP_NAME%] 运行中，PID=%PID%，端口=%APP_PORT%
) else (
    echo [%APP_NAME%] 未运行
)
goto :end

:: ============================================
:: 查找进程 PID（通过端口号匹配）
:: ============================================
:find_pid
set PID=
for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%APP_PORT% "') do (
    set PID=%%a
)
goto :eof

:end
if "%1"=="" (
    echo.
    pause
)
endlocal
\r