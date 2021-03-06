local function fails(str, exp)
	local ok, err = loadstring("return " .. str, "=x")
	assert(not ok)
	if err ~= exp then error(("Expected %s,\n got %s"):format(exp, err, 0)) end
end

local function ok(str, exp)
	local ok = loadstring("return " .. str, "=x")
	assert(ok)
end

fails("1a", "x:1: malformed number near '1a'")
fails("0..0", "x:1: malformed number near '0..0'")

ok("1")
ok("0xff")
ok("0XFF")
ok("1e-23+3")
