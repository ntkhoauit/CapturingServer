opts = maxOps.mxsCmdLineArgs
root = opts[#root]
baseFolder = opts[#baseFolder]

fn getFilesRecursive root pattern =
(
	dir_array = GetDirectories (root + "/*")
	print ("root " + root)

	for d in dir_array do
		join dir_array (GetDirectories (d + "/*"))

	my_files = #()
	join my_files (getFiles (root + "/" + pattern))
	for f in dir_array do
		join my_files (getFiles (f + pattern))
	my_files
)

files = getFilesRecursive root "*.max"

fn getViewportSafeframeDib =
(
 local x = y = 0, v
 local s = getViewSize()
 local k = getRendImageAspect()

 if k < (s.x/s.y) then
 (
  v = [s.y*k,s.y]
  x = (s.x-v.x)/2 + 1
 )
 else
 (
  v = [s.x, s.x/k]
  y = (s.y-v.y)/2 + 1
 )
 bmp = bitmap v.x v.y
 pasteBitmap (gw.getViewportDib()) bmp (box2 x y v.x v.y) [0,0]
 bmp
)

fn viewport_snapshot fname frame dirname =
(
	snapshot_name = dirname + "//" + fname;
	view_size = getViewSize()
	sliderTime = frame
	dib = getViewportSafeframeDib()
	dib.filename = snapshot_name
	save dib
	close dib
	gc()
)

fn load_texture root =
(
	parent_root = getFilenamePath (trimRight root "\\/")
	dir_parent_array = GetDirectories (parent_root + "/*")
	files = #()
	texture = false
	for dir in dir_parent_array do
	(
		if findString dir "maps" != undefined
		then (
			join files (getFiles (dir + "*.jpg"))
			join files (getFiles (dir + "*.jpeg"))
			join files (getFiles (dir + "*.png"))
			join files (getFiles (dir + "*.bmp"))
			texture = true
		)
		if findString dir "texture" != undefined
		then (
			join files (getFiles (dir + "*.jpg"))
			join files (getFiles (dir + "*.jpeg"))
			join files (getFiles (dir + "*.png"))
			join files (getFiles (dir + "*.bmp"))
			texture = true
		)
		if findString dir "textures" != undefined
        then (
            join files (getFiles (dir + "*.jpg"))
            join files (getFiles (dir + "*.jpeg"))
            join files (getFiles (dir + "*.png"))
            join files (getFiles (dir + "*.bmp"))
            texture = true
        )
        if findString dir "map" != undefined
        then (
            join files (getFiles (dir + "*.jpg"))
            join files (getFiles (dir + "*.jpeg"))
            join files (getFiles (dir + "*.png"))
            join files (getFiles (dir + "*.bmp"))
            texture = true
        )
	)
	if texture == false then(
		join files (getFilesRecursive root "*.jpg")
		join files (getFilesRecursive root "*.jpeg")
		join files (getFilesRecursive root "*.png")
		join files (getFilesRecursive root "*.bmp")
	)
	files
)


setVRaySilentMode()
makeDir baseFolder
for f in files do (
    loadMAXFile f useFileUnits:true quiet:true
    viewport.ResetAllViews()
    displaySafeFrames = true

    max tool maximize
    viewport.setGridVisibility #all false
    actionMan.executeAction 0 "550"
    viewport.SetShowEdgeFaces false
    viewport.EnableSolidBackgroundColorMode true
    filepath = getFilenamePath f
    filename = getFilenameFile f
    theAxis = (viewport.getTM()).row3
    newFolder = filepath + filename

    theName = getfilenamepath filepath

    bMaps = getClassInstances Bitmaptexture

    folder3dpath = theName
    print ("folder path " + folder3dpath)
    --load texture
    files = load_texture folder3dpath
    for matClass in material.classes do (
        for m in (getclassinstances matClass processChildren:true) do (
            if (refs.dependentNodes m).count>0 then (
                if classof m != Multimaterial then showTexturemap m on
            )
        )
    )

    if files.count != 0
    then (
        for i = 1 to bMaps.count do (
            if bMaps[i].filename != undefined
            then(
                currImgName = filenameFromPath(bMaps[i].filename)
                print ("File " + i as string + ":  " + bMaps[i].filename)
                index = undefined
                for f in files do (
                    index = findString f currImgName --returns 12
                    if index != undefined
                    then
                    (
                        print("replace:" + bMaps[i].filename + "  to  " + f)
                        bMaps[i].filename = f
                        exit
                    )
                )
                if index == undefined
                then
                (
                    r = random 1 files.count
                    bMaps[i].filename = files[r]
                )
            )
            else(
                r = random 1 files.count
                bMaps[i].filename = files[r]
            )
        )
    )

    i = 1
    for i = 1 to 12 do
    (
        tempFolder = newFolder + "_" + i as string
        actionMan.executeAction 0 "311"
        viewport.zoom .75
        viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder
        viewport.rotate (quat 30 theAxis)
        completeredraw()
    )

    for i = 13 to 24 do
    (
        tempFolder = newFolder + "_" + i as string
        actionMan.executeAction 0 "311"
        viewport.zoom .55
        viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder
        viewport.rotate (quat 30 theAxis)
        completeredraw()
    )

    i += 1
    viewport.setType #view_front
    actionMan.executeAction 0 "311"
    viewport.zoom 1.2
    completeredraw()
    viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder

    i += 1
    viewport.setType #view_back
    actionMan.executeAction 0 "311"
    viewport.zoom 1.2
    completeredraw()
    viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder

    i += 1
    viewport.setType #view_left
    actionMan.executeAction 0 "311"
    viewport.zoom 1.2
    completeredraw()
    viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder

    i += 1
    viewport.setType #view_right
    actionMan.executeAction 0 "311"
    viewport.zoom 1.2
    completeredraw()
    viewport_snapshot (filename + "_" + i as string + ".jpg") 60f baseFolder
)


