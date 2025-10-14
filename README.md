# SFS汉化安装器

> [!WARNING]
> 该项目目前处于Alpha阶段，许多功能尚未完成且可能存在严重BUG！  
> 开发者正在努力开发中，敬请关注

用于 [Spaceflight Simulator](https://spaceflightsimulator.com/) 的 **自定义翻译（Custom Translations）** 的便捷式安装器。

旨在简化SFS汉化过程，降低安装门槛。

> ~~*由于开发者水平有限，本项目在代码质量与项目架构上可能存在一些问题。*~~
> 
> 欢迎提出PR！

*喜欢请点个 Star :)*

### 下载

> 目前只提供 CI版本
- [GitHub Actions（CI版）](https://github.com/youfeng11/SFS-CustomTranslations-Installer/actions/workflows/android.yml)

### 接入本项目

- 请阅读 **[自定义语言接入指南](INTEGRATE.md)**

## 计划

- [x] 常见授权方式
  - Android 10-
    - [x] 存储权限授权
  - Android 11+
    - [x] 使用SAF授权
    - [x] 使用漏洞授权
    - [x] 使用Shizuku/Sui权限授权
    - [x] 使用ROOT权限授权
- [ ] 自定义翻译安装
- [ ] 基本设置功能
- [ ] 更多功能...

## 捐赠

<img src="https://pan.tenire.com/view.php/40a71c2ac20505ac131046925d138129.png" width="300" alt="微信">

## 开源协议

[**GNU General Public License v3 (GPL-3)**](LICENSE)
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
