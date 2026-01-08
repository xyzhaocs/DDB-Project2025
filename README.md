# 分布式数据库项目 (DDB)

[OptionSourceCode](./OptionSourceCode)文件夹包含可运行的分布式旅行预订系统的源代码。

## 构建

在 `OptionSourceCode` 目录下运行以下命令：

```bash
. build.sh
```
## 开启系统

```bash
. start_system.sh
```

## 测试

4. 客户端（测试用例 1-4）
```bash
java -cp bin transaction.Client
```

5. 客户端故障测试用例（单独运行）
```bash
java -cp bin transaction.Client case5
java -cp bin transaction.Client case6
```
## 关闭系统

```bash
. stop_system.sh
```

## 配置

- 端口和 RMI 名称在 `conf/ddb.conf` 文件中配置。
- 每个资源管理器使用影子分页将数据存储在 `data` 目录下。
