# Merged into `atom-basic-steps`

改名/改描述这个动作已经由 `atom-basic-steps` 的 `action: 'rename'` 实现，而且参数完全一样
（`displayName` / `description` / `fromLog` / `matchTemplate`）——同一个动作被实现了两遍。

```groovy
// 旧
runAtom(atom: 'build-name-setter', version: '1.latest', inputs: [displayName: '#${BUILD_NUMBER}'])
// 新
runAtom(atom: 'atom-basic-steps', version: '1.latest', inputs: [action: 'rename', displayName: '#${BUILD_NUMBER}'])
```

本仓库只读保留，便于历史构建追溯。
