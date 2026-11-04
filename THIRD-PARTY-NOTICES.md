# Third-Party Notices

The MOTD plugin jar bundles the components listed below. They are **not** covered
by the MOTD license and remain under the terms of their respective authors, as
stated in Section 9 of [LICENSE](LICENSE).

Both components are relocated into the plugin's own package space by the Maven
Shade plugin, so they are present in the compiled jar even though their package
names differ from the originals.

| Component | Version | License | Package inside the jar |
|---|---|---|---|
| SnakeYAML | 2.3 | Apache License 2.0 | `me.D4rkSn0w.motd.libs.snakeyaml` |
| bStats (bstats-base) | 3.2.1 | MIT License | `me.D4rkSn0w.motd.libs.bstats` |

Anyone redistributing this software — as source code or as a compiled jar — must
keep this file and the license texts it refers to.

---

## SnakeYAML

- Project: https://bitbucket.org/snakeyaml/snakeyaml
- Artifact: `org.yaml:snakeyaml:2.3`
- License: Apache License 2.0
- Full license text: [licenses/Apache-License-2.0.txt](licenses/Apache-License-2.0.txt)

```
Copyright (c) 2008, SnakeYAML

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## bStats

- Project: https://bstats.org
- Source: https://github.com/Bastian/bStats-Metrics
- Artifact: `org.bstats:bstats-base:3.2.1`
- License: MIT License
- Full license text: [licenses/MIT-bStats.txt](licenses/MIT-bStats.txt)

```
MIT License

Copyright (c) 2021 Bastian Oppermann

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Not bundled

These are compiled against but provided by the server at runtime, so they are
not contained in the jar and need no notice here: Paper/Bukkit API, Velocity
API, BungeeCord API, SpongeAPI and the Adventure library.
