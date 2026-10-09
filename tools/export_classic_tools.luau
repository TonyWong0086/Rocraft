--[[
Rocraft: export the classic tools in your own Roblox Studio place (StarterPack) for Rocraft.
Nothing here is shipped with the mod; this writes to YOUR machine only.

1. powershell -ExecutionPolicy Bypass -File tools/receive_export.ps1   (listens on 127.0.0.1:18723, saves to %TEMP%\rbx\legacy)
2. Run this file in Studio's command bar (HttpService must be allowed in the place).
3. java tools/Rgba.java %TEMP%\rbx\legacy <Rocraft profile>\config\rocraft\legacy   (RGBA dumps -> PNG)

Meshes are re-read with EditableMesh (so rbxasset:// files that modern Roblox only streams still export),
images with EditableImage, and each Tool is written as a small .rbxmx (Grip, Handle, SpecialMesh, Sounds).
Built-in .wav sounds can't be read back from Studio; Rocraft uses Minecraft sounds for those.
]]
local AS, HS = game:GetService("AssetService"), game:GetService("HttpService")
local function post(path, body) HS:PostAsync("http://127.0.0.1:18723/?p=" .. HS:UrlEncode(path), body, Enum.HttpContentType.TextPlain) end
local function f(x) return string.format("%.4f", x) end
local done = {}
local function export(uri)
	if done[uri] or not uri:match("^rbxasset://") then return end
	done[uri] = true
	local rel = uri:gsub("^rbxasset://", ""):gsub("\\", "/"):gsub("//+", "/")
	pcall(function()
		if rel:lower():match("%.mesh$") then
			local m = AS:CreateEditableMeshAsync(Content.fromUri(uri))
			local faces = m:GetFaces()
			local parts = {"version 1.01\n" .. #faces .. "\n"}
			for _, fid in ipairs(faces) do
				local vs, ns, us = m:GetFaceVertices(fid), m:GetFaceNormals(fid), m:GetFaceUVs(fid)
				for k = 1, 3 do
					local p = m:GetPosition(vs[k])
					local n = ns and ns[k] and m:GetNormal(ns[k]) or Vector3.yAxis
					local uv = us and us[k] and m:GetUV(us[k]) or Vector2.zero
					parts[#parts + 1] = "[" .. f(p.X) .. "," .. f(p.Y) .. "," .. f(p.Z) .. "][" .. f(n.X) .. "," .. f(n.Y) .. "," .. f(n.Z) .. "][" .. f(uv.X) .. "," .. f(1 - uv.Y) .. ",0]"
				end
			end
			post(rel, table.concat(parts))
		elseif not rel:lower():match("%.wav$") and not rel:lower():match("%.mp3$") then
			local img = AS:CreateEditableImageAsync(Content.fromUri(uri))
			post(rel .. ".rgba" .. img.Size.X .. "x" .. img.Size.Y, buffer.tostring(img:ReadPixelsBuffer(Vector2.zero, img.Size)))
		end
	end)
end
local function esc(s) return (tostring(s):gsub("&", "&amp;"):gsub("<", "&lt;"):gsub(">", "&gt;")) end
local function v3(n, v) return string.format('<Vector3 name="%s"><X>%f</X><Y>%f</Y><Z>%f</Z></Vector3>', n, v.X, v.Y, v.Z) end
local function cf(n, c)
	local x, y, z, r00, r01, r02, r10, r11, r12, r20, r21, r22 = c:GetComponents()
	return string.format('<CoordinateFrame name="%s"><X>%f</X><Y>%f</Y><Z>%f</Z><R00>%f</R00><R01>%f</R01><R02>%f</R02><R10>%f</R10><R11>%f</R11><R12>%f</R12><R20>%f</R20><R21>%f</R21><R22>%f</R22></CoordinateFrame>', n, x, y, z, r00, r01, r02, r10, r11, r12, r20, r21, r22)
end
local function str(n, s) return string.format('<string name="%s">%s</string>', n, esc(s)) end
local function content(n, s) return string.format('<Content name="%s"><url>%s</url></Content>', n, esc(s)) end
for _, tool in ipairs(game.StarterPack:GetChildren()) do
	if tool:IsA("Tool") then
		export(tool.TextureId)
		local x = {'<roblox version="4"><Item class="Tool"><Properties>', str("Name", tool.Name), cf("Grip", tool.Grip), content("TextureId", tool.TextureId), '</Properties>'}
		local h = tool:FindFirstChild("Handle")
		if h then
			x[#x + 1] = '<Item class="' .. h.ClassName .. '"><Properties>' .. str("Name", "Handle") .. v3("size", h.Size)
			if h:IsA("MeshPart") then x[#x + 1] = content("MeshId", h.MeshId) .. content("TextureID", h.TextureID); export(h.MeshId); export(h.TextureID) end
			x[#x + 1] = string.format('<Color3uint8 name="Color3uint8">%d</Color3uint8>', h.Color.R * 255 * 65536 + h.Color.G * 255 * 256 + h.Color.B * 255) .. '</Properties>'
			local m = h:FindFirstChildOfClass("SpecialMesh")
			if m then
				export(m.MeshId); export(m.TextureId)
				x[#x + 1] = '<Item class="SpecialMesh"><Properties>' .. str("Name", m.Name) .. content("MeshId", m.MeshId) .. content("TextureId", m.TextureId) .. v3("Scale", m.Scale) .. v3("Offset", m.Offset) .. '</Properties></Item>'
			end
			x[#x + 1] = '</Item>'
		end
		for _, s in ipairs(tool:GetDescendants()) do
			if s:IsA("Sound") then x[#x + 1] = '<Item class="Sound"><Properties>' .. str("Name", s.Name) .. content("SoundId", s.SoundId) .. '</Properties></Item>' end
		end
		x[#x + 1] = '</Item></roblox>'
		post("tools/" .. tool.Name .. ".rbxmx", table.concat(x))
	end
end
pcall(function() HS:GetAsync("http://127.0.0.1:18723/?p=STOP") end)
print("Rocraft export done")
