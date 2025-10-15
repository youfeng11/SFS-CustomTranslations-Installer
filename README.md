# SFS汉化安装器

> [!WARNING]
> 本项目目前处于 **Alpha 阶段**，许多功能尚未完善，且可能存在严重 BUG！  
> 开发者正在努力开发中，敬请关注！

为 [Spaceflight Simulator](https://spaceflightsimulator.com/) 提供**自定义翻译（Custom Translations）**的便捷安装工具，旨在简化 SFS 汉化流程，降低使用门槛。

*如果您喜欢本项目，请点个 Star 支持一下 :)*

> ~~*由于开发者水平有限，本项目在代码质量与项目架构上可能存在一些问题。*~~
> 
> 欢迎提出PR！

### 功能亮点

- **简单易用**：一键安装汉化包，降低汉化门槛。
- **多平台支持**：兼容多种 Android 版本及权限授权方式。
- **开放生态**：支持社区贡献的自定义翻译，欢迎接入您的翻译包！

## 下载

> 当前仅提供 CI 构建版本，正式版敬请期待！

- [GitHub Actions（CI 版）](https://github.com/youfeng11/SFS-CustomTranslations-Installer/actions/workflows/android.yml)

## 接入本项目

SFS 汉化安装器支持在应用内选择多种自定义翻译包。您可以将自己的翻译包接入我们的平台，在应用中展示！我们致力于打造一个高质量、合规的翻译集合，感谢每位贡献者的支持。

- 详情请阅读 **[自定义翻译接入指南](INTEGRATE.md)**。

## 开发计划

- [x] 常见授权方式支持
  - Android 10-
    - [x] 存储权限授权
  - Android 11+
    - [x] SAF（存储访问框架）授权
    - [x] 漏洞授权
    - [x] Shizuku/Sui 权限授权
    - [x] ROOT 权限授权
- [ ] 自定义翻译安装功能
- [ ] 基础设置界面
- [ ] 更多功能开发中...

## 捐赠

支持项目开发，欢迎通过以下方式捐赠：

<img src="https://pan.tenire.com/view.php/40a71c2ac20505ac131046925d138129.png" width="300" alt="微信支付">

## 开源协议

本项目采用 [**GNU General Public License v3 (GPL-3)**](LICENSE)。
```
Copyright (C) 2025  由风

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

## 贡献者

<a href="https://github.com/youfeng11/SFS-CustomTranslations-Installer/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=youfeng11/SFS-CustomTranslations-Installer"  alt="贡献者"/>
</a>

## 鸣谢
- [Dhizuku](https://github.com/iamr0s/Dhizuku): 借鉴部分UI设计
- [KernelSU](https://github.com/tiann/KernelSU): 借鉴部分UI设计
- [libsu](https://github.com/topjohnwu/libsu): 针对使用 root 权限的应用程序的完整解决方案
- [Shizuku-API](https://github.com/RikkaApps/Shizuku-API): 通过 ADB 或 root 权限，以标准方式安全、便捷地调用系统级 API
- [SFS简体中文语言包](https://gitee.com/YouFeng11/SFS-zh-CN-Translation): 默认使用的汉化包，由本项目开发者维护
- [航天模拟器简体中文汉化包](https://github.com/sTheNight/Spaceflight-Simulator-CNlang): 由可爱的重铬酸钠制作的汉化包，在此感谢他的支持
- ~~[SimpleStorage](https://github.com/anggrayudi/SimpleStorage): 为简化SAF~~
