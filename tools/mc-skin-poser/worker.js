/**
 * =====================================================================
 * MC Skin Poser — 単一ファイル版 Cloudflare Worker
 * =====================================================================
 * アップロード方法:
 *   Cloudflare ダッシュボード → Workers & Pages → 新しい Worker を作成 →
 *   Quick Edit にこのファイルの内容を貼り付けて「デプロイ」。
 *   これだけで動きます（ビルド不要・追加ファイル不要）。
 *
 * 機能:
 *   - /api/skin?name=<プレイヤー名> : Mojang API からスキンを取得（5分キャッシュ）
 *   - /                            : 3D ポーザー UI（HTML/CSS/JS をこのファイル内に内蔵）
 *
 * クライアント側:
 *   - skinview3d (Three.js) でスキン付きモデルを描画
 *   - 部位ごとの数値スライダー + 3Dクリック選択 + 回転/移動ギズモ
 *   - 手持ちアイテム（公式アイテムテクスチャ）・防具（公式アーマーレイヤーテクスチャ）・
 *     エンチャント光沢
 *   - 2K (2560x1440) 背景透過 PNG 保存
 * =====================================================================
 */

// ---------------------------------------------------------------------------
// クライアント JS（バッククォート・${} を使用しないよう注意して書く）
// ---------------------------------------------------------------------------
const APP_JS =
"import * as THREE from 'https://esm.sh/three@0.160.0';\n" +
"import { SkinViewer, createOrbitControls } from 'https://esm.sh/skinview3d@3';\n" +
"import { TransformControls } from 'https://esm.sh/three@0.160.0/examples/jsm/controls/TransformControls.js';\n" +
"\n" +
"// ---------- helpers ----------\n" +
"const $ = (id) => document.getElementById(id);\n" +
"const PARTS = ['head','body','rightArm','leftArm','rightLeg','leftLeg'];\n" +
"const PART_LABEL = { head:'頭', body:'体', rightArm:'右腕', leftArm:'左腕', rightLeg:'右足', leftLeg:'左足' };\n" +
"const DEG = (d) => THREE.MathUtils.degToRad(d);\n" +
"const RAD = (r) => THREE.MathUtils.radToDeg(r);\n" +
"const TEXTURE_BASE = 'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/master/assets/minecraft';\n" +
"\n" +
"const state = {\n" +
"  parts: {},\n" +
"  selected: null,\n" +
"  armor: {},\n" +
"  armorMaterial: 'iron',\n" +
"  rightItem: null,\n" +
"  leftItem: null,\n" +
"  itemTextures: {},\n" +
"  armorTextures: {},\n" +
"  glintItemTex: null,\n" +
"  glintArmorTex: null,\n" +
"};\n" +
"\n" +
"// ---------- viewer ----------\n" +
"const canvas = $('canvas');\n" +
"const viewer = new SkinViewer({ canvas, width: 900, height: 700 });\n" +
"const orbit = createOrbitControls(viewer);\n" +
"viewer.scene.background = new THREE.Color('#1a1a2e');\n" +
"\n" +
"const grid = new THREE.GridHelper(12, 12, 0x777788, 0x3a3a44);\n" +
"viewer.scene.add(grid);\n" +
"const ground = new THREE.Mesh(\n" +
"  new THREE.PlaneGeometry(60, 60),\n" +
"  new THREE.ShadowMaterial({ opacity: 0.3 })\n" +
");\n" +
"ground.rotation.x = -Math.PI / 2;\n" +
"ground.receiveShadow = true;\n" +
"viewer.scene.add(ground);\n" +
"\n" +
"try {\n" +
"  viewer.renderer.shadowMap.enabled = true;\n" +
"  viewer.renderer.shadowMap.type = THREE.PCFSoftShadowMap;\n" +
"  viewer.scene.traverse((o) => {\n" +
"    if (o.isDirectionalLight) {\n" +
"      o.castShadow = true;\n" +
"      o.shadow.mapSize.set(1024, 1024);\n" +
"      o.shadow.camera.near = 1; o.shadow.camera.far = 80;\n" +
"      o.shadow.camera.left = -20; o.shadow.camera.right = 20;\n" +
"      o.shadow.camera.top = 20; o.shadow.camera.bottom = -20;\n" +
"    }\n" +
"  });\n" +
"} catch (e) { console.warn('shadow setup failed', e); }\n" +
"\n" +
"// ---------- transform gizmo ----------\n" +
"let transform = null;\n" +
"try {\n" +
"  transform = new TransformControls(viewer.camera, canvas);\n" +
"  transform.setMode('rotate');\n" +
"  transform.addEventListener('dragging-changed', (e) => { orbit.enabled = !e.value; });\n" +
"  transform.addEventListener('objectChange', () => { if (state.selected) syncSlidersFor(state.selected); });\n" +
"  viewer.scene.add(transform);\n" +
"} catch (e) { console.warn('TransformControls unavailable', e); }\n" +
"\n" +
"// ---------- texture loading with fallback ----------\n" +
"function makeGlintTexture() {\n" +
"  const c = document.createElement('canvas');\n" +
"  c.width = 64; c.height = 64;\n" +
"  const g = c.getContext('2d');\n" +
"  g.clearRect(0, 0, 64, 64);\n" +
"  for (let i = -64; i < 160; i += 14) {\n" +
"    g.fillStyle = 'rgba(255,255,255,0.55)'; g.fillRect(i, 0, 5, 64);\n" +
"    g.fillStyle = 'rgba(200,120,255,0.45)'; g.fillRect(i + 5, 0, 5, 64);\n" +
"    g.fillStyle = 'rgba(120,200,255,0.30)'; g.fillRect(i + 10, 0, 4, 64);\n" +
"  }\n" +
"  const tex = new THREE.CanvasTexture(c);\n" +
"  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;\n" +
"  return tex;\n" +
"}\n" +
"\n" +
"function loadTexture(url, fallbackFn) {\n" +
"  return new Promise((resolve) => {\n" +
"    new THREE.TextureLoader().load(url, (tex) => {\n" +
"      tex.wrapS = tex.wrapT = THREE.RepeatWrapping;\n" +
"      tex.colorSpace = THREE.SRGBColorSpace;\n" +
"      resolve(tex);\n" +
"    }, undefined, () => resolve(fallbackFn ? fallbackFn() : null));\n" +
"  });\n" +
"}\n" +
"\n" +
"function glintItemTexture() {\n" +
"  if (!state.glintItemTex) {\n" +
"    state.glintItemTex = loadTexture(TEXTURE_BASE + '/textures/misc/enchanted_glint_item.png', makeGlintTexture);\n" +
"  }\n" +
"  return state.glintItemTex;\n" +
"}\n" +
"function glintArmorTexture() {\n" +
"  if (!state.glintArmorTex) {\n" +
"    state.glintArmorTex = loadTexture(TEXTURE_BASE + '/textures/misc/enchanted_glint.png', makeGlintTexture);\n" +
"  }\n" +
"  return state.glintArmorTex;\n" +
"}\n" +
"\n" +
"// ---------- parts ----------\n" +
"function bindParts() {\n" +
"  const skin = viewer.playerObject && viewer.playerObject.skin;\n" +
"  if (!skin) { setStatus('モデル初期化に失敗しました', true); return; }\n" +
"  for (const id of PARTS) {\n" +
"    const obj = skin[id];\n" +
"    if (!obj) continue;\n" +
"    state.parts[id] = obj;\n" +
"    obj.userData.part = id;\n" +
"    obj.traverse((m) => { m.userData.part = id; m.castShadow = true; });\n" +
"  }\n" +
"  buildSliderUI();\n" +
"  applyPosePreset('idle');\n" +
"  frameModel();\n" +
"}\n" +
"\n" +
"// ---------- pose ----------\n" +
"const PRESETS = {\n" +
"  idle: { rightArm:[0,0,0], leftArm:[0,0,0], rightLeg:[0,0,0], leftLeg:[0,0,0], head:[0,0,0], body:[0,0,0] },\n" +
"  walk: { rightArm:[-40,0,0], leftArm:[40,0,0], rightLeg:[40,0,0], leftLeg:[-40,0,0], head:[0,12,0], body:[0,0,0] },\n" +
"  run:  { rightArm:[-75,0,0], leftArm:[75,0,0], rightLeg:[75,0,0], leftLeg:[-75,0,0], body:[12,0,0] },\n" +
"  sit:  { rightLeg:[-90,0,0], leftLeg:[-90,0,0], body:[-6,0,0], rightArm:[8,0,0], leftArm:[8,0,0] },\n" +
"  tpose:{ rightArm:[0,0,-90], leftArm:[0,0,90] },\n" +
"  wave: { rightArm:[-170,0,-80], head:[0,-20,0], body:[-5,0,0] },\n" +
"  point:{ rightArm:[-85,0,0], leftArm:[-60,0,0], head:[0,8,0] },\n" +
"  flex: { rightArm:[-160,0,0], leftArm:[-160,0,0], rightLeg:[-25,0,0], leftLeg:[-25,0,0], body:[5,0,0] },\n" +
"};\n" +
"\n" +
"function applyPosePreset(name) {\n" +
"  const p = PRESETS[name];\n" +
"  if (!p) return;\n" +
"  Object.keys(p).forEach((id) => {\n" +
"    const v = p[id];\n" +
"    const obj = state.parts[id];\n" +
"    if (obj) obj.rotation.set(DEG(v[0]), DEG(v[1]), DEG(v[2]));\n" +
"  });\n" +
"  PARTS.forEach(syncSlidersFor);\n" +
"}\n" +
"\n" +
"function setPartAxis(id, axis, deg) {\n" +
"  const obj = state.parts[id];\n" +
"  if (!obj) return;\n" +
"  if (axis === 'x') obj.rotation.x = DEG(deg);\n" +
"  else if (axis === 'y') obj.rotation.y = DEG(deg);\n" +
"  else obj.rotation.z = DEG(deg);\n" +
"}\n" +
"\n" +
"function syncSlidersFor(id) {\n" +
"  const obj = state.parts[id];\n" +
"  if (!obj) return;\n" +
"  document.querySelectorAll('input[data-part=\"' + id + '\"]').forEach((inp) => {\n" +
"    const axis = inp.dataset.axis;\n" +
"    const deg = Math.round(RAD(obj.rotation[axis]));\n" +
"    if (inp.type === 'range') inp.value = String(deg);\n" +
"    else inp.value = String(deg);\n" +
"  });\n" +
"}\n" +
"\n" +
"// ---------- selection ----------\n" +
"function setEmissive(obj, hex) {\n" +
"  obj.traverse((m) => {\n" +
"    if (!m.isMesh || !m.material) return;\n" +
"    const mats = Array.isArray(m.material) ? m.material : [m.material];\n" +
"    mats.forEach((mat) => { if (mat.emissive) mat.emissive.setHex(hex); });\n" +
"  });\n" +
"}\n" +
"\n" +
"function selectPart(id) {\n" +
"  if (state.selected && state.parts[state.selected]) {\n" +
"    setEmissive(state.parts[state.selected], 0x000000);\n" +
"  }\n" +
"  state.selected = id;\n" +
"  $('partSelect').value = id || '';\n" +
"  document.querySelectorAll('.sliders .part').forEach((el) => {\n" +
"    el.classList.toggle('selected', el.dataset.part === id);\n" +
"  });\n" +
"  if (id && state.parts[id]) setEmissive(state.parts[id], 0x2a2a44);\n" +
"  if (transform) {\n" +
"    if (id && state.parts[id]) transform.attach(state.parts[id]);\n" +
"    else transform.detach();\n" +
"  }\n" +
"  if (id) syncSlidersFor(id);\n" +
"}\n" +
"\n" +
"const raycaster = new THREE.Raycaster();\n" +
"const ndc = new THREE.Vector2();\n" +
"canvas.addEventListener('click', (e) => {\n" +
"  const rect = canvas.getBoundingClientRect();\n" +
"  ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;\n" +
"  ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;\n" +
"  raycaster.setFromCamera(ndc, viewer.camera);\n" +
"  const meshes = [];\n" +
"  PARTS.forEach((id) => {\n" +
"    const obj = state.parts[id];\n" +
"    if (obj) obj.traverse((m) => m.isMesh && meshes.push(m));\n" +
"  });\n" +
"  const hits = raycaster.intersectObjects(meshes, false);\n" +
"  if (hits.length && hits[0].object.userData.part) {\n" +
"    selectPart(hits[0].object.userData.part);\n" +
"  } else {\n" +
"    selectPart(null);\n" +
"  }\n" +
"});\n" +
"\n" +
"// ---------- sliders UI ----------\n" +
"function buildSliderUI() {\n" +
"  const wrap = $('sliders');\n" +
"  wrap.innerHTML = '';\n" +
"  PARTS.forEach((id) => {\n" +
"    const grp = document.createElement('div');\n" +
"    grp.className = 'part';\n" +
"    grp.dataset.part = id;\n" +
"    const header = document.createElement('div');\n" +
"    header.className = 'part-header';\n" +
"    header.textContent = PART_LABEL[id];\n" +
"    header.addEventListener('click', () => selectPart(state.selected === id ? null : id));\n" +
"    grp.appendChild(header);\n" +
"    ['x','y','z'].forEach((axis) => {\n" +
"      const row = document.createElement('div');\n" +
"      row.className = 'axis';\n" +
"      const lab = document.createElement('span');\n" +
"      lab.textContent = axis.toUpperCase();\n" +
"      const range = document.createElement('input');\n" +
"      range.type = 'range'; range.min = -180; range.max = 180; range.step = 1;\n" +
"      range.dataset.part = id; range.dataset.axis = axis;\n" +
"      const num = document.createElement('input');\n" +
"      num.type = 'number'; num.min = -180; num.max = 180; num.step = 1;\n" +
"      num.dataset.part = id; num.dataset.axis = axis;\n" +
"      const apply = () => {\n" +
"        const v = parseFloat(range.value) || 0;\n" +
"        num.value = String(Math.round(v));\n" +
"        setPartAxis(id, axis, v);\n" +
"      };\n" +
"      range.addEventListener('input', apply);\n" +
"      num.addEventListener('change', () => { range.value = num.value; apply(); });\n" +
"      row.append(lab, range, num);\n" +
"      grp.appendChild(row);\n" +
"    });\n" +
"    wrap.appendChild(grp);\n" +
"  });\n" +
"}\n" +
"\n" +
"// ---------- armor (real textures) ----------\n" +
"// 公式アーマーレイヤーテクスチャ (64x32) をバニラのモデル UV で箱に貼る\n" +
"const ARMOR_UVS = {\n" +
"  helmet: { front:[8,0,16,8], right:[0,0,8,8], left:[16,0,24,8], back:[24,0,32,8], top:[8,8,16,16], bottom:[16,8,24,16] },\n" +
"  torso:  { front:[20,16,28,28], right:[16,16,20,28], left:[36,16,40,28], back:[28,16,36,28], top:[20,12,28,16], bottom:[28,12,36,16] },\n" +
"  arm:    { front:[44,16,48,28], right:[40,16,44,28], left:[52,16,56,28], back:[48,16,52,28], top:[44,12,48,16], bottom:[48,12,52,16] },\n" +
"  legs:   { front:[4,16,12,28], right:[0,16,4,28], left:[20,16,24,28], back:[12,16,20,28], top:[4,12,12,16], bottom:[12,12,20,16] },\n" +
"  boots:  { front:[20,16,28,28], right:[16,16,20,28], left:[36,16,40,28], back:[28,16,36,28], top:[20,12,28,16], bottom:[28,12,36,16] },\n" +
"};\n" +
"\n" +
"function armorBoxGeometry(w, h, d, uvRect) {\n" +
"  const geo = new THREE.BoxGeometry(w, h, d);\n" +
"  const pos = geo.attributes.position;\n" +
"  const nor = geo.attributes.normal;\n" +
"  const uv = geo.attributes.uv;\n" +
"  for (let i = 0; i < pos.count; i++) {\n" +
"    const nx = nor.getX(i), ny = nor.getY(i), nz = nor.getZ(i);\n" +
"    const px = pos.getX(i), py = pos.getY(i), pz = pos.getZ(i);\n" +
"    let rect = uvRect.front, u = (px + w / 2) / w, v = (py + h / 2) / h;\n" +
"    if (Math.abs(nx) > 0.5) { rect = nx > 0 ? uvRect.right : uvRect.left; u = (pz + d / 2) / d; v = (py + h / 2) / h; if (nx < 0) u = 1 - u; }\n" +
"    else if (Math.abs(ny) > 0.5) { rect = ny > 0 ? uvRect.top : uvRect.bottom; u = (px + w / 2) / w; v = (pz + d / 2) / d; if (ny < 0) v = 1 - v; }\n" +
"    else if (nz < 0) { rect = uvRect.back; u = 1 - u; }\n" +
"    const ru0 = rect[0] / 64, rv0 = rect[1] / 32, ru1 = rect[2] / 64, rv1 = rect[3] / 32;\n" +
"    uv.setXY(i, ru0 + u * (ru1 - ru0), rv0 + v * (rv1 - rv0));\n" +
"  }\n" +
"  uv.needsUpdate = true;\n" +
"  return geo;\n" +
"}\n" +
"\n" +
"function armorTextures(material) {\n" +
"  if (!state.armorTextures[material]) {\n" +
"    const base = TEXTURE_BASE + '/textures/models/armor/' + material;\n" +
"    state.armorTextures[material] = {\n" +
"      layer1: loadTexture(base + '_layer_1.png', makeGlintTexture),\n" +
"      layer2: loadTexture(base + '_layer_2.png', makeGlintTexture),\n" +
"    };\n" +
"  }\n" +
"  return state.armorTextures[material];\n" +
"}\n" +
"\n" +
"function bboxOf(obj) {\n" +
"  obj.updateWorldMatrix(true, true);\n" +
"  const box = new THREE.Box3().setFromObject(obj);\n" +
"  box.applyMatrix4(obj.matrixWorld.clone().invert());\n" +
"  const size = box.getSize(new THREE.Vector3());\n" +
"  return { box, size, min: box.min, max: box.max };\n" +
"}\n" +
"\n" +
"function addArmorMesh(parent, geometry, material) {\n" +
"  const mesh = new THREE.Mesh(geometry, material);\n" +
"  mesh.castShadow = true;\n" +
"  parent.add(mesh);\n" +
"  return mesh;\n" +
"}\n" +
"\n" +
"function clearArmor() {\n" +
"  Object.keys(state.armor).forEach((k) => {\n" +
"    (state.armor[k].meshes || []).forEach((m) => m.removeFromParent());\n" +
"    (state.armor[k].glint || []).forEach((m) => m.removeFromParent());\n" +
"  });\n" +
"  state.armor = {};\n" +
"}\n" +
"\n" +
"function armorOptions() {\n" +
"  const out = { glint: $('armorGlint').checked, material: state.armorMaterial };\n" +
"  ['helmet','chest','legs','boots'].forEach((p) => {\n" +
"    out[p] = {\n" +
"      on: document.querySelector('.armorChk[data-part=\"' + p + '\"]').checked,\n" +
"      color: document.querySelector('.armorColor[data-part=\"' + p + '\"]').value,\n" +
"    };\n" +
"  });\n" +
"  return out;\n" +
"}\n" +
"\n" +
"async function rebuildArmor() {\n" +
"  clearArmor();\n" +
"  if (!state.parts.head) return;\n" +
"  const opts = armorOptions();\n" +
"  const tex = armorTextures(opts.material);\n" +
"  const layer1 = await tex.layer1;\n" +
"  const layer2 = await tex.layer2;\n" +
"  if (layer1 == null) { setStatus('防具テクスチャの取得に失敗しました（CDN確認）', true); return; }\n" +
"  const isLeather = opts.material === 'leather';\n" +
"  const mk = (map) => new THREE.MeshStandardMaterial({\n" +
"    map: map, color: isLeather ? 0xffffff : 0xffffff,\n" +
"    roughness: 0.55, metalness: isLeather ? 0.05 : 0.45,\n" +
"  });\n" +
"\n" +
"  // ヘルメット\n" +
"  if (opts.helmet.on) {\n" +
"    const b = bboxOf(state.parts.head);\n" +
"    const w = b.size.x + 0.6, h = b.size.y + 0.6, d = b.size.z + 0.6;\n" +
"    const geo = armorBoxGeometry(w, h, d, ARMOR_UVS.helmet);\n" +
"    geo.translate(b.center.x, b.center.y, b.center.z);\n" +
"    const mat = mk(layer1);\n" +
"    if (isLeather) mat.color.set(opts.helmet.color);\n" +
"    state.armor.helmet = { meshes: [addArmorMesh(state.parts.head, geo, mat)], glint: [] };\n" +
"  }\n" +
"  // チェスト（体 + 両腕）\n" +
"  if (opts.chest.on) {\n" +
"    const b = bboxOf(state.parts.body);\n" +
"    const w = b.size.x + 0.7, h = b.size.y + 0.7, d = b.size.z + 0.7;\n" +
"    const geo = armorBoxGeometry(w, h, d, ARMOR_UVS.torso);\n" +
"    geo.translate(b.center.x, b.center.y, b.center.z);\n" +
"    const mat = mk(layer1);\n" +
"    if (isLeather) mat.color.set(opts.chest.color);\n" +
"    state.armor.chest = { meshes: [addArmorMesh(state.parts.body, geo, mat)], glint: [] };\n" +
"    ['rightArm','leftArm'].forEach((armId) => {\n" +
"      const ab = bboxOf(state.parts[armId]);\n" +
"      const aw = ab.size.x + 0.6, ah = ab.size.y + 0.6, ad = ab.size.z + 0.6;\n" +
"      const ageo = armorBoxGeometry(aw, ah, ad, ARMOR_UVS.arm);\n" +
"      ageo.translate(ab.center.x, ab.center.y, ab.center.z);\n" +
"      state.armor.chest.meshes.push(addArmorMesh(state.parts[armId], ageo, mat));\n" +
"    });\n" +
"  }\n" +
"  // レギンス / ブーツ（両足）\n" +
"  if (opts.legs.on || opts.boots.on) {\n" +
"    state.armor.legs = { meshes: [], glint: [] };\n" +
"    state.armor.boots = { meshes: [], glint: [] };\n" +
"    ['rightLeg','leftLeg'].forEach((legId) => {\n" +
"      const b = bboxOf(state.parts[legId]);\n" +
"      const w = b.size.x + 0.55, d = b.size.z + 0.55;\n" +
"      if (opts.legs.on) {\n" +
"        const hh = b.size.y * 0.66;\n" +
"        const geo = armorBoxGeometry(w, hh, d, ARMOR_UVS.legs);\n" +
"        geo.translate(b.center.x, b.max.y - hh / 2, b.center.z);\n" +
"        const mat = mk(layer2);\n" +
"        if (isLeather) mat.color.set(opts.legs.color);\n" +
"        state.armor.legs.meshes.push(addArmorMesh(state.parts[legId], geo, mat));\n" +
"      }\n" +
"      if (opts.boots.on) {\n" +
"        const hh = b.size.y * 0.36;\n" +
"        const geo = armorBoxGeometry(w, hh, d, ARMOR_UVS.boots);\n" +
"        geo.translate(b.center.x, b.min.y + hh / 2, b.center.z);\n" +
"        const mat = mk(layer2);\n" +
"        if (isLeather) mat.color.set(opts.boots.color);\n" +
"        state.armor.boots.meshes.push(addArmorMesh(state.parts[legId], geo, mat));\n" +
"      }\n" +
"    });\n" +
"  }\n" +
"  if (opts.glint) applyGlintToArmor();\n" +
"}\n" +
"\n" +
"async function applyGlintToArmor() {\n" +
"  const tex = await glintArmorTexture();\n" +
"  if (!tex) return;\n" +
"  Object.keys(state.armor).forEach((k) => {\n" +
"    const rec = state.armor[k];\n" +
"    if (!rec) return;\n" +
"    rec.meshes.forEach((mesh) => {\n" +
"      const glint = new THREE.Mesh(mesh.geometry.clone(), new THREE.MeshStandardMaterial({\n" +
"        map: tex, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false,\n" +
"      }));\n" +
"      glint.scale.set(1.03, 1.03, 1.03);\n" +
"      mesh.parent.add(glint);\n" +
"      rec.glint.push(glint);\n" +
"    });\n" +
"  });\n" +
"}\n" +
"\n" +
"// ---------- held items (real textures) ----------\n" +
"const ITEM_FILES = {\n" +
"  sword: 'diamond_sword', axe: 'netherite_axe', pickaxe: 'netherite_pickaxe',\n" +
"  bow: 'bow', shield: 'shield', golden_apple: 'golden_apple', torch: 'torch',\n" +
"  trident: 'trident', totem: 'totem_of_undying',\n" +
"};\n" +
"\n" +
"function itemTexture(kind) {\n" +
"  const file = ITEM_FILES[kind];\n" +
"  if (!file) return Promise.resolve(null);\n" +
"  if (!state.itemTextures[kind]) {\n" +
"    state.itemTextures[kind] = loadTexture(\n" +
"      TEXTURE_BASE + '/textures/item/' + file + '.png',\n" +
"      () => null\n" +
"    );\n" +
"  }\n" +
"  return state.itemTextures[kind];\n" +
"}\n" +
"\n" +
"function makeItemPlane(texture) {\n" +
"  const mat = new THREE.MeshBasicMaterial({\n" +
"    map: texture, transparent: true, alphaTest: 0.1, side: THREE.DoubleSide, depthWrite: false,\n" +
"  });\n" +
"  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1.0, 1.0), mat);\n" +
"  return mesh;\n" +
"}\n" +
"\n" +
"async function rebuildItems() {\n" +
"  clearItems();\n" +
"  if (!state.parts.rightArm) return;\n" +
"  const right = $('rightItem').value;\n" +
"  const left = $('leftItem').value;\n" +
"  const glintOn = $('itemGlint').checked;\n" +
"  if (right) state.rightItem = await buildHeldItem('rightArm', right, glintOn);\n" +
"  if (left) state.leftItem = await buildHeldItem('leftArm', left, glintOn);\n" +
"}\n" +
"\n" +
"async function buildHeldItem(armId, kind, glintOn) {\n" +
"  const arm = state.parts[armId];\n" +
"  const tex = await itemTexture(kind);\n" +
"  const group = new THREE.Group();\n" +
"  if (tex) {\n" +
"    const plane = makeItemPlane(tex);\n" +
"    group.add(plane);\n" +
"    if (glintOn) {\n" +
"      const gt = await glintItemTexture();\n" +
"      if (gt) {\n" +
"        const glint = new THREE.Mesh(new THREE.PlaneGeometry(1.02, 1.02), new THREE.MeshBasicMaterial({\n" +
"          map: gt, transparent: true, opacity: 0.6, blending: THREE.AdditiveBlending, depthWrite: false,\n" +
"        }));\n" +
"        group.add(glint);\n" +
"      }\n" +
"    }\n" +
"  } else {\n" +
"    setStatus('アイテムテクスチャの取得に失敗しました（CDN確認）', true);\n" +
"  }\n" +
"  viewer.scene.add(group);\n" +
"  group.userData.arm = armId;\n" +
"  return group;\n" +
"}\n" +
"\n" +
"function clearItems() {\n" +
"  if (state.rightItem) state.rightItem.removeFromParent();\n" +
"  if (state.leftItem) state.leftItem.removeFromParent();\n" +
"  state.rightItem = null;\n" +
"  state.leftItem = null;\n" +
"}\n" +
"\n" +
"// アイテムを手の位置に追従（カメラ向きのビルボード）\n" +
"function updateItems() {\n" +
"  [state.rightItem, state.leftItem].forEach((group) => {\n" +
"    if (!group) return;\n" +
"    const arm = state.parts[group.userData.arm];\n" +
"    if (!arm) return;\n" +
"    arm.updateWorldMatrix(true, false);\n" +
"    const b = bboxOf(arm);\n" +
"    const hand = new THREE.Vector3(0, b.min.y + 2.6, 1.3);\n" +
"    hand.applyMatrix4(arm.matrixWorld);\n" +
"    group.position.copy(hand);\n" +
"    group.lookAt(viewer.camera.position);\n" +
"  });\n" +
"}\n" +
"\n" +
"// ---------- camera / framing ----------\n" +
"function frameModel() {\n" +
"  if (!viewer.playerObject) return;\n" +
"  const box = new THREE.Box3().setFromObject(viewer.playerObject);\n" +
"  const size = box.getSize(new THREE.Vector3());\n" +
"  const center = box.getCenter(new THREE.Vector3());\n" +
"  ground.position.y = box.min.y;\n" +
"  grid.position.y = box.min.y;\n" +
"  const dist = Math.max(size.x, size.z) * 1.6 + size.y * 0.9 + 3;\n" +
"  viewer.camera.position.set(center.x + dist * 0.7, center.y + dist * 0.55, center.z + dist);\n" +
"  orbit.target.copy(center);\n" +
"  orbit.update();\n" +
"}\n" +
"\n" +
"function resetCamera() { frameModel(); }\n" +
"\n" +
"// ---------- skin loading ----------\n" +
"function setStatus(text, isError) {\n" +
"  const el = $('status');\n" +
"  el.textContent = text;\n" +
"  el.classList.toggle('error', !!isError);\n" +
"}\n" +
"\n" +
"async function loadPlayer(name) {\n" +
"  if (!name) return;\n" +
"  setStatus('取得中…');\n" +
"  try {\n" +
"    const res = await fetch('/api/skin?name=' + encodeURIComponent(name));\n" +
"    const data = await res.json();\n" +
"    if (!res.ok) { setStatus('エラー: ' + (data.message || data.error || res.status), true); return; }\n" +
"    setStatus(data.name + '（' + (data.slim ? 'スリム' : 'クラシック') + '）を読み込み中…');\n" +
"    await viewer.loadSkin(data.skinUrl, { model: data.slim ? 'slim' : 'default' });\n" +
"    if (data.capeUrl) viewer.loadCape(data.capeUrl).catch(() => {});\n" +
"    bindParts();\n" +
"    rebuildArmor();\n" +
"    rebuildItems();\n" +
"    setStatus(data.name + ' を表示中。ポーズを調整してください。');\n" +
"  } catch (e) {\n" +
"    console.error(e);\n" +
"    setStatus('通信エラー: ' + e, true);\n" +
"  }\n" +
"}\n" +
"\n" +
"// ---------- 2K transparent screenshot ----------\n" +
"function download(url, filename) {\n" +
"  const a = document.createElement('a');\n" +
"  a.href = url;\n" +
"  a.download = filename;\n" +
"  a.click();\n" +
"}\n" +
"\n" +
"function screenshot() {\n" +
"  const W = 2560, H = 1440;\n" +
"  const wasBg = viewer.scene.background;\n" +
"  const cw = $('canvasWrap').clientWidth, chh = $('canvasWrap').clientHeight;\n" +
"  const gridVis = grid.visible;\n" +
"  if (transform) transform.visible = false;\n" +
"  grid.visible = false;\n" +
"  ground.visible = false;\n" +
"  viewer.scene.background = null;\n" +
"  viewer.renderer.setClearColor(0x000000, 0);\n" +
"  viewer.renderer.setSize(W, H, false);\n" +
"  viewer.camera.aspect = W / H;\n" +
"  viewer.camera.updateProjectionMatrix();\n" +
"  viewer.render();\n" +
"  let url = null;\n" +
"  try { url = viewer.canvas.toDataURL('image/png'); } catch (e) { console.error(e); }\n" +
"  // 復元\n" +
"  viewer.scene.background = wasBg;\n" +
"  viewer.renderer.setClearColor(0x000000, 1);\n" +
"  viewer.renderer.setSize(cw, chh, false);\n" +
"  viewer.camera.aspect = cw / chh;\n" +
"  viewer.camera.updateProjectionMatrix();\n" +
"  grid.visible = gridVis;\n" +
"  ground.visible = true;\n" +
"  if (transform) transform.visible = true;\n" +
"  viewer.render();\n" +
"  if (!url) { setStatus('スクリーンショット生成に失敗', true); return; }\n" +
"  download(url, 'mcskin_thumb_2k.png');\n" +
"  setStatus('2K PNG（背景透過）を保存しました。');\n" +
"}\n" +
"\n" +
"// ---------- resize ----------\n" +
"function resize() {\n" +
"  const w = $('canvasWrap').clientWidth, h = $('canvasWrap').clientHeight;\n" +
"  if (w <= 0 || h <= 0) return;\n" +
"  viewer.renderer.setSize(w, h, false);\n" +
"  viewer.camera.aspect = w / h;\n" +
"  viewer.camera.updateProjectionMatrix();\n" +
"}\n" +
"\n" +
"// ---------- tick ----------\n" +
"function tick() {\n" +
"  requestAnimationFrame(tick);\n" +
"  updateItems();\n" +
"  viewer.render();\n" +
"}\n" +
"\n" +
"// ---------- events ----------\n" +
"$('loadBtn').addEventListener('click', () => loadPlayer($('nameInput').value.trim()));\n" +
"$('nameInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPlayer(e.target.value.trim()); });\n" +
"$('poseSelect').addEventListener('change', (e) => applyPosePreset(e.target.value));\n" +
"$('partSelect').addEventListener('change', (e) => selectPart(e.target.value || null));\n" +
"$('modeSelect').addEventListener('change', (e) => { if (transform) transform.setMode(e.target.value); });\n" +
"$('armorMat').addEventListener('change', (e) => { state.armorMaterial = e.target.value; rebuildArmor(); });\n" +
"$('rightItem').addEventListener('change', rebuildItems);\n" +
"$('leftItem').addEventListener('change', rebuildItems);\n" +
"$('itemGlint').addEventListener('change', rebuildItems);\n" +
"document.querySelectorAll('.armorChk').forEach((c) => c.addEventListener('change', rebuildArmor));\n" +
"document.querySelectorAll('.armorColor').forEach((c) => c.addEventListener('input', rebuildArmor));\n" +
"$('armorGlint').addEventListener('change', rebuildArmor);\n" +
"$('gridToggle').addEventListener('change', (e) => { grid.visible = e.target.checked; });\n" +
"$('bgColor').addEventListener('input', (e) => { viewer.scene.background = new THREE.Color(e.target.value); });\n" +
"$('resetCam').addEventListener('click', resetCamera);\n" +
"$('shotBtn').addEventListener('click', screenshot);\n" +
"window.addEventListener('resize', resize);\n" +
"\n" +
"// ---------- init ----------\n" +
"setTimeout(resize, 60);\n" +
"tick();\n";

// ---------------------------------------------------------------------------
// クライアント CSS
// ---------------------------------------------------------------------------
const APP_CSS =
":root{--bg:#101014;--panel:#17171d;--panel2:#1f1f28;--border:#2c2c38;--text:#e8e8ea;--muted:#9a9aa5;--accent:#6aa9ff}\n" +
"*{box-sizing:border-box;margin:0;padding:0}html,body{height:100%}\n" +
"body{background:var(--bg);color:var(--text);font-family:'Hiragino Kaku Gothic ProN','Noto Sans JP',system-ui,sans-serif;overflow:hidden}\n" +
"#app{display:flex;height:100vh}\n" +
"#panel{width:320px;min-width:320px;background:var(--panel);border-right:1px solid var(--border);overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px}\n" +
"#panel h1{font-size:18px;letter-spacing:.5px}\n" +
"#panel .sub{font-size:11px;color:var(--muted);margin-top:-4px}\n" +
"hr{border:none;border-top:1px solid var(--border);margin:4px 0}\n" +
".row{display:flex;align-items:center;gap:8px;flex-wrap:wrap}\n" +
".block{display:flex;flex-direction:column;gap:4px;font-size:12px;color:var(--muted)}\n" +
".inline{display:inline-flex;align-items:center;gap:4px}\n" +
"input[type=text]{flex:1;min-width:0;background:var(--panel2);border:1px solid var(--border);border-radius:6px;color:var(--text);padding:7px 9px;font-size:13px;outline:none}\n" +
"input[type=text]:focus{border-color:var(--accent)}\n" +
"select{background:var(--panel2);border:1px solid var(--border);border-radius:6px;color:var(--text);padding:6px 8px;font-size:13px;outline:none;width:100%}\n" +
".row select{width:auto;flex:1}\n" +
"button{background:var(--accent);border:none;border-radius:6px;color:#0b0b10;font-weight:bold;padding:7px 12px;font-size:13px;cursor:pointer}\n" +
".status{font-size:12px;color:var(--muted);min-height:16px;word-break:break-all}\n" +
".status.error{color:#ff7b7b}\n" +
"fieldset{border:1px solid var(--border);border-radius:8px;padding:8px 10px;display:flex;flex-direction:column;gap:7px;font-size:12px}\n" +
"fieldset legend{font-size:11px;color:var(--muted);padding:0 4px}\n" +
"fieldset label{color:var(--muted)}\n" +
"input[type=color]{width:34px;height:24px;border:1px solid var(--border);border-radius:4px;background:none;padding:0;cursor:pointer}\n" +
".hint{font-size:11px;color:var(--muted);line-height:1.6}\n" +
".sliders{display:flex;flex-direction:column;gap:8px;max-height:330px;overflow-y:auto}\n" +
".sliders .part{background:var(--panel2);border:1px solid var(--border);border-radius:8px;padding:6px 8px}\n" +
".sliders .part.selected{border-color:var(--accent)}\n" +
".part-header{font-size:12px;font-weight:bold;cursor:pointer;padding:2px 0 4px;color:var(--text)}\n" +
".part-header:hover{color:var(--accent)}\n" +
".axis{display:grid;grid-template-columns:18px 1fr 52px;align-items:center;gap:6px;font-size:11px;color:var(--muted);padding:1px 0}\n" +
".axis input[type=range]{width:100%;accent-color:var(--accent)}\n" +
".axis input[type=number]{width:52px;background:var(--bg);border:1px solid var(--border);border-radius:4px;color:var(--text);padding:3px 4px;font-size:11px;text-align:right}\n" +
"#canvasWrap{flex:1;position:relative;background:#1a1a2e}\n" +
"#canvas{position:absolute;inset:0;width:100%;height:100%;display:block}\n" +
"::-webkit-scrollbar{width:8px}::-webkit-scrollbar-thumb{background:#333;border-radius:4px}\n";

// ---------------------------------------------------------------------------
// クライアント HTML
// ---------------------------------------------------------------------------
const APP_HTML =
"<!DOCTYPE html>\n" +
"<html lang=\"ja\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
"<title>MC Skin Poser</title><style>" + APP_CSS + "</style></head>\n" +
"<body><div id=\"app\">\n" +
"<aside id=\"panel\">\n" +
"<h1>&#127912; MC Skin Poser</h1><p class=\"sub\">スキン取得 → ポーズ → 2K透過サムネイル保存</p>\n" +
"<div class=\"row\"><input id=\"nameInput\" type=\"text\" placeholder=\"プレイヤー名 (例: Steve)\" maxlength=\"16\"><button id=\"loadBtn\">取得</button></div>\n" +
"<div id=\"status\" class=\"status\">プレイヤー名を入力して「取得」</div><hr>\n" +
"<label class=\"block\">ポーズプリセット<select id=\"poseSelect\">\n" +
"<option value=\"idle\">アイドル</option><option value=\"walk\">歩く</option><option value=\"run\">走る</option>\n" +
"<option value=\"sit\">座る</option><option value=\"tpose\">Tポーズ</option><option value=\"wave\">手を振る</option>\n" +
"<option value=\"point\">指差し</option><option value=\"flex\">力こぶ</option></select></label><hr>\n" +
"<div class=\"row\"><label class=\"block\">部位<select id=\"partSelect\"><option value=\"\">(3Dでクリック選択)</option>\n" +
"<option value=\"head\">頭</option><option value=\"body\">体</option><option value=\"rightArm\">右腕</option>\n" +
"<option value=\"leftArm\">左腕</option><option value=\"rightLeg\">右足</option><option value=\"leftLeg\">左足</option></select></label>\n" +
"<label class=\"block\">モード<select id=\"modeSelect\"><option value=\"rotate\">回転</option><option value=\"translate\">移動</option></select></label></div>\n" +
"<div id=\"sliders\" class=\"sliders\"></div><hr>\n" +
"<fieldset><legend>持たせるアイテム（実テクスチャ）</legend>\n" +
"<div class=\"row\"><label>右手</label><select id=\"rightItem\"><option value=\"\">なし</option>\n" +
"<option value=\"sword\">剣</option><option value=\"axe\">斧</option><option value=\"pickaxe\">ツルハシ</option>\n" +
"<option value=\"bow\">弓</option><option value=\"shield\">盾</option><option value=\"golden_apple\">金リンゴ</option>\n" +
"<option value=\"torch\">たいまつ</option><option value=\"trident\">トライデント</option><option value=\"totem\">トーテム</option></select></div>\n" +
"<div class=\"row\"><label>左手</label><select id=\"leftItem\"><option value=\"\">なし</option>\n" +
"<option value=\"sword\">剣</option><option value=\"axe\">斧</option><option value=\"pickaxe\">ツルハシ</option>\n" +
"<option value=\"bow\">弓</option><option value=\"shield\">盾</option><option value=\"golden_apple\">金リンゴ</option>\n" +
"<option value=\"torch\">たいまつ</option><option value=\"trident\">トライデント</option><option value=\"totem\">トーテム</option></select></div>\n" +
"<label class=\"block\"><input type=\"checkbox\" id=\"itemGlint\"> エンチャント光沢</label></fieldset>\n" +
"<fieldset><legend>防具（公式アーマーテクスチャ）</legend>\n" +
"<label class=\"block\">素材<select id=\"armorMat\">\n" +
"<option value=\"leather\">レザー（染色可）</option><option value=\"chainmail\">チェーンメイル</option>\n" +
"<option value=\"iron\" selected>鉄</option><option value=\"gold\">金</option><option value=\"diamond\">ダイヤモンド</option>\n" +
"<option value=\"netherite\">ネザライト</option></select></label>\n" +
"<div class=\"row\"><label><input type=\"checkbox\" class=\"armorChk\" data-part=\"helmet\"> ヘルメット</label><input type=\"color\" class=\"armorColor\" data-part=\"helmet\" value=\"#8a4b2f\"></div>\n" +
"<div class=\"row\"><label><input type=\"checkbox\" class=\"armorChk\" data-part=\"chest\"> チェストプレート</label><input type=\"color\" class=\"armorColor\" data-part=\"chest\" value=\"#3d5a80\"></div>\n" +
"<div class=\"row\"><label><input type=\"checkbox\" class=\"armorChk\" data-part=\"legs\"> レギンス</label><input type=\"color\" class=\"armorColor\" data-part=\"legs\" value=\"#3d5a80\"></div>\n" +
"<div class=\"row\"><label><input type=\"checkbox\" class=\"armorChk\" data-part=\"boots\"> ブーツ</label><input type=\"color\" class=\"armorColor\" data-part=\"boots\" value=\"#2f3e46\"></div>\n" +
"<label class=\"block\"><input type=\"checkbox\" id=\"armorGlint\"> エンチャント光沢</label></fieldset>\n" +
"<fieldset><legend>表示 / 出力</legend>\n" +
"<div class=\"row\"><label>背景</label><input type=\"color\" id=\"bgColor\" value=\"#1a1a2e\"><label class=\"inline\"><input type=\"checkbox\" id=\"gridToggle\" checked> グリッド</label></div>\n" +
"<div class=\"row\"><button id=\"resetCam\">カメラリセット</button><button id=\"shotBtn\">&#128247; 2K透過PNG保存</button></div></fieldset>\n" +
"<p class=\"hint\">モデルをクリックで部位選択 → ギズモで回転/移動。左メニューの数値でも微調整。<br>保存は 2560×1440・背景透過です。</p>\n" +
"</aside>\n" +
"<div id=\"canvasWrap\"><canvas id=\"canvas\"></canvas></div>\n" +
"</div><script type=\"module\">" + APP_JS + "</script></body></html>\n";

// ---------------------------------------------------------------------------
// Worker: fetch ハンドラ
// ---------------------------------------------------------------------------
const CACHE_TTL = 300;
const UA = "mc-skin-poser/0.2 (Cloudflare Workers)";

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    const url = new URL(request.url);

    if (url.pathname === "/api/skin") {
      const name = (url.searchParams.get("name") || "").trim();
      if (!name || name.length > 16) {
        return json({ error: "missing_name", message: "プレイヤー名を指定してください。" }, 400);
      }
      return handleSkin(name, request);
    }

    if (url.pathname.startsWith("/api/")) {
      return json({ error: "not_found", message: "API が見つかりません。" }, 404);
    }

    // ルート・アセット → 内蔵 HTML（単一ファイル版）
    return new Response(APP_HTML, {
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "public, max-age=60",
        ...corsHeaders(),
      },
    });
  },
};

async function handleSkin(name, request) {
  const cacheKey = new Request("https://skin-api.invalid/" + encodeURIComponent(name), request);
  const cache = caches.default;
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  const profileRes = await fetch(
    "https://api.mojang.com/users/profiles/minecraft/" + encodeURIComponent(name),
    { headers: { "User-Agent": UA } }
  );

  if (profileRes.status === 204 || profileRes.status === 404) {
    return json({ error: "player_not_found", message: "プレイヤー「" + name + "」は見つかりませんでした。" }, 404);
  }
  if (profileRes.status === 429) {
    return json({ error: "rate_limited", message: "Mojang API がレート制限中です。少し待って再試行してください。" }, 429);
  }
  if (!profileRes.ok) {
    return json({ error: "mojang_error", status: profileRes.status }, 502);
  }

  const profile = await profileRes.json();
  const uuid = profile.id;

  const sessionRes = await fetch(
    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid,
    { headers: { "User-Agent": UA } }
  );
  if (!sessionRes.ok) {
    return json({ error: "session_error", status: sessionRes.status }, 502);
  }
  const session = await sessionRes.json();

  const texturesProp = (session.properties || []).find((p) => p.name === "textures");
  if (!texturesProp) {
    return json({ error: "no_textures", message: "このプレイヤーにはスキンデータがありません。" }, 404);
  }

  let textures = {};
  try {
    textures = JSON.parse(atob(texturesProp.value));
  } catch {
    return json({ error: "bad_textures", message: "テクスチャデータの解析に失敗しました。" }, 502);
  }

  const skin = textures && textures.textures && textures.textures.SKIN;
  const cape = textures && textures.textures && textures.textures.CAPE;

  const result = {
    uuid,
    name: profile.name || name,
    skinUrl: skin ? skin.url : null,
    slim: !!(skin && skin.metadata && skin.metadata.model === "slim"),
    capeUrl: cape ? cape.url : null,
  };

  const response = json(result);
  response.headers.set("Cache-Control", "public, max-age=" + CACHE_TTL);
  await cache.put(cacheKey, response.clone());
  return response;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...corsHeaders(),
    },
  });
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}
