/*
 *  sscanf 2.15.1
 *
 *  Version: MPL 1.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is the sscanf 2.0 SA:MP plugin.
 *
 *  The Initial Developer of the Original Code is Alex "Y_Less" Cole.
 *  Portions created by the Initial Developer are Copyright (c) 2022
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *
 *      Cheaterman
 *      DEntisT
 *      Emmet_
 *      karimcambridge
 *      kalacsparty
 *      Kirima
 *      leHeix
 *      maddinat0r
 *      Southclaws
 *      Y_Less
 *      ziggi
 *
 *  Special Thanks to:
 *
 *      SA:MP Team past, present, and future.
 *      maddinat0r, for hosting the repo for a very long time.
 *      Emmet_, for his efforts in maintaining it for almost a year.
 */

#include <string.h>
#include "sscanf.h"
#include "args.h"
#include "utils.h"
#include "data.h"

extern unsigned int
	g_iTrueMax;

extern logprintf_t
	logprintf,
	real_logprintf;

void
	qlog(char const *, ...);

int
	gAlpha = 0xFF,
	gForms = -1;

float
	gNameSimilarity = -1.0f;

// Options:
//  
//  1 = OLD_DEFAULT_NAME
//  
//    Parse values in "U(5)" as if they were a name that must be connected,
//    instead of just any number.
//  
//  2 = MATCH_NAME_PARTIAL
//  
//    When searching for players, match any part of their name not just the
//    start.
//  
//  4 = CELLMIN_ON_MATCHES
//  
//    If multiple player name matches are found, return 0x80000000.
//  
//  8 = SSCANF_QUIET
//  
//    Disable all prints.
//  
// 16 = OLD_DEFAULT_KUSTOM
//
//    `K(def)` needs a valid input as the default value.
//
// 32 = MATCH_NAME_FIRST
//
//    Search for the best name match, not just the first.
//
// 64 = MATCH_NAME_SIMILAR
//
//    Use the similarity search code to find the best name.
//
E_SSCANF_OPTIONS
	gOptions = SSCANF_OPTIONS_NONE;
	
cell * args_s::Next()
{
	if (HasMore())
	{
		cell * cptr;
		amx_GetAddr(Amx, Params[Pos++], &cptr);
		return cptr;
	}
	return NULL;
};

void args_s::Mark()
{
	Marker = Pos;
};

void args_s::Restore()
{
	Pos = Marker;
};

bool args_s::HasMore()
{
	return Pos < Count;
};

void
	RestoreOpts(E_SSCANF_OPTIONS opt, int alpha, int forms)
{
	gOptions = opt;
	gAlpha = alpha;
	gForms = forms;
	if (gOptions & SSCANF_QUIET) logprintf = qlog;
	else logprintf = real_logprintf;
}

void
	SetOptions(char * name, cell value)
{
	// Not the most flexible code I've ever written...
	if (!strincmp(name, "OLD_DEFAULT_NAME", 16))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | OLD_DEFAULT_NAME);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~OLD_DEFAULT_NAME);
			break;
		case -1:
			if (*(name + 16) == '=')
			{
				if (*(name + 17) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~OLD_DEFAULT_NAME);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | OLD_DEFAULT_NAME);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "MATCH_NAME_PARTIAL", 18))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | MATCH_NAME_PARTIAL);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~MATCH_NAME_PARTIAL);
			break;
		case -1:
			if (*(name + 18) == '=')
			{
				if (*(name + 19) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~MATCH_NAME_PARTIAL);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | MATCH_NAME_PARTIAL);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "ERROR_CODE_IN_RET", 17))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | ERROR_CODE_IN_RET);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~ERROR_CODE_IN_RET);
			break;
		case -1:
			if (*(name + 17) == '=')
			{
				if (*(name + 19) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~ERROR_CODE_IN_RET);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | ERROR_CODE_IN_RET);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "WARNINGS_AS_ERRORS", 18))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | WARNINGS_AS_ERRORS);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~WARNINGS_AS_ERRORS);
			break;
		case -1:
			if (*(name + 18) == '=')
			{
				if (*(name + 19) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~WARNINGS_AS_ERRORS);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | WARNINGS_AS_ERRORS);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "ERROR_CATEGORY_ONLY", 19))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | ERROR_CATEGORY_ONLY);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~ERROR_CATEGORY_ONLY);
			break;
		case -1:
			if (*(name + 19) == '=')
			{
				if (*(name + 20) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~ERROR_CATEGORY_ONLY);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | ERROR_CATEGORY_ONLY);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "CELLMIN_ON_MATCHES", 18))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | CELLMIN_ON_MATCHES);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~CELLMIN_ON_MATCHES);
			break;
		case -1:
			if (*(name + 18) == '=')
			{
				if (*(name + 19) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~CELLMIN_ON_MATCHES);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | CELLMIN_ON_MATCHES);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "SSCANF_QUIET", 12))
	{
		switch (value)
		{
		case 1:
			logprintf = qlog;
			gOptions = (E_SSCANF_OPTIONS)(gOptions | SSCANF_QUIET);
			break;
		case 0:
			logprintf = real_logprintf;
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~SSCANF_QUIET);
			break;
		case -1:
			if (*(name + 12) == '=')
			{
				if (*(name + 13) == '0')
				{
					logprintf = real_logprintf;
					gOptions = (E_SSCANF_OPTIONS)(gOptions & ~SSCANF_QUIET);
				}
				else
				{
					logprintf = qlog;
					gOptions = (E_SSCANF_OPTIONS)(gOptions | SSCANF_QUIET);
				}
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "SSCANF_ALPHA", 12))
	{
		if (value == -1)
		{
			if (*(name + 12) == '=')
			{
				gAlpha = (atoi(name + 13) & 0xFF) | (gAlpha & 0xFFFFFF00);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
		else
		{
			gAlpha = (value & 0xFF) | (gAlpha & 0xFFFFFF00);
		}
	}
	else if (!strincmp(name, "SSCANF_COLOUR_FORMS", 19))
	{
		if (value == -1)
		{
			if (*(name + 19) == '=')
			{
				gForms = atoi(name + 20);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
		else
		{
			gForms = value;
		}
	}
	else if (!strincmp(name, "SSCANF_ARGB", 11))
	{
		switch (value)
		{
		case 1:
			gAlpha = (gAlpha & 0xFF) | 0x100;
			break;
		case 0:
			gAlpha = (gAlpha & 0xFF);
			break;
		case -1:
			if (*(name + 11) == '=')
			{
				if (*(name + 12) == '0') gAlpha = (gAlpha & 0xFF);
				else gAlpha = (gAlpha & 0xFF) | 0x100;
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "OLD_DEFAULT_KUSTOM", 18) || !strincmp(name, "OLD_DEFAULT_CUSTOM", 18))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | OLD_DEFAULT_KUSTOM);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~OLD_DEFAULT_KUSTOM);
			break;
		case -1:
			if (*(name + 18) == '=')
			{
				if (*(name + 19) == '0') gOptions = (E_SSCANF_OPTIONS)(gOptions & ~OLD_DEFAULT_KUSTOM);
				else gOptions = (E_SSCANF_OPTIONS)(gOptions | OLD_DEFAULT_KUSTOM);
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "MATCH_NAME_FIRST", 16))
	{
		switch (value)
		{
		case 1:
			gOptions = (E_SSCANF_OPTIONS)(gOptions | MATCH_NAME_FIRST);
			break;
		case 0:
			gOptions = (E_SSCANF_OPTIONS)(gOptions & ~MATCH_NAME_FIRST);
			break;
		case -1:
			if (*(name + 16) == '=')
			{
				if (*(name + 17) == '0')
				{
					gOptions = (E_SSCANF_OPTIONS)(gOptions & ~MATCH_NAME_FIRST);
				}
				else
				{
					gOptions = (E_SSCANF_OPTIONS)(gOptions | MATCH_NAME_FIRST);
				}
			}
			else
			{
				SscanfError(29, "No option value.");
			}
		}
	}
	else if (!strincmp(name, "MATCH_NAME_SIMILARITY", 21))
	{
		// The MSB is set on negative floats and ints, so this works.
		if (value < 0)
		{
			if (*(name + 21) == '=')
			{
				gNameSimilarity = (float)atof(name + 22);
				if (gNameSimilarity < 0.0f || gNameSimilarity > 1.0f)
				{
					// Invalid value.
					gNameSimilarity = -1.0f;
				}
			}
			else
			{
				gNameSimilarity = -1.0f;
			}
		}
		else
		{
			gNameSimilarity = amx_ctof(value);
			if (gNameSimilarity > 1.0f)
			{
				// Invalid value.
				gNameSimilarity = -1.0f;
			}
		}
	}
	else
	{
		SscanfError(30, "Unknown option name.");
	}
}

cell
	GetOptions(char * name)
{
	// Not the most flexible code I've ever written...
	if (!strincmp(name, "OLD_DEFAULT_NAME", 16))
	{
		return (gOptions & OLD_DEFAULT_NAME) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "MATCH_NAME_PARTIAL", 18))
	{
		return (gOptions & MATCH_NAME_PARTIAL) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "CELLMIN_ON_MATCHES", 18))
	{
		return (gOptions & CELLMIN_ON_MATCHES) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "SSCANF_QUIET", 12))
	{
		return (gOptions & SSCANF_QUIET) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "ERROR_CODE_IN_RET", 17))
	{
		return (gOptions & ERROR_CODE_IN_RET) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "WARNINGS_AS_ERRORS", 18))
	{
		return (gOptions & WARNINGS_AS_ERRORS) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "ERROR_CATEGORY_ONLY", 19))
	{
		return (gOptions & ERROR_CATEGORY_ONLY) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "SSCANF_ALPHA", 12))
	{
		return (gAlpha & 0xFF);
	}
	else if (!strincmp(name, "SSCANF_COLOUR_FORMS", 19))
	{
		return gForms;
	}
	else if (!strincmp(name, "SSCANF_ARGB", 11))
	{
		return (gAlpha >> 8) & 1;
	}
	else if (!strincmp(name, "OLD_DEFAULT_KUSTOM", 18) || !strincmp(name, "OLD_DEFAULT_CUSTOM", 18))
	{
		return (gOptions & OLD_DEFAULT_KUSTOM) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "MATCH_NAME_FIRST", 16))
	{
		return (gOptions & MATCH_NAME_FIRST) != SSCANF_OPTIONS_NONE;
	}
	else if (!strincmp(name, "MATCH_NAME_SIMILARITY", 21))
	{
		return amx_ftoc(gNameSimilarity);
	}
	else
	{
		SscanfError(30, "Unknown option name.");
	}
	return -1;
}

char
	GetSingleType(char ** format)
{
	char
		* cur = *format,
		tmp = *cur;
	if (tmp == '<')
	{
		++cur;
		char
			ret = *cur;
		if (ret)
		{
			++cur;
			if (*cur == '>')
			{
				*format += 3;
				return ret;
			}
		}
	}
	if (tmp)
	{
		SscanfError(13, "Unenclosed specifier parameter.", tmp);
		++(*format);
		return ' ';
	}
	else
	{
		SscanfWarning(14, "No specified parameter found.");
		return ' ';
	}
}

char *
	GetMultiType(char ** format)
{
	char
		* cur = *format,
		* ret = cur;
	if (*cur == '<')
	{
		++ret;
		bool
			reop = false,
			escape = false;
		do
		{
			if (*cur == '\\')
			{
				escape = !escape;
			}
			else
			{
				escape = false;
			}
			++cur;
			if (!escape)
			{
				if (reop)
				{
					if (*cur == '>')
					{
						reop = false;
						++cur;
					}
				}
				else
				{
					if (*cur == '<')
					{
						reop = true;
						++cur;
					}
				}
			}
		}
		while (*cur && (reop || escape || *cur != '>'));
		if (*cur)
		{
			// Only gets here if there is a closing '>'.
			*cur = '\0';
			*format = cur + 1;
			return ret;
		}
		else
		{
			SscanfError(65, "Unclosed specifier parameters.");
		}
	}
	else
	{
		SscanfError(66, "No specified parameters found.");
	}
	*format = cur;
	return 0;
}

unsigned int
	GetUserString(char * string, char ** end)
{
	unsigned int
		id = 0;
	char
		cur;
	// Get the full name, they can't contain spaces in this code.  If a
	// player's name manages to contain spaces (e.g. via SetPlayerName), then
	// you can only do the part of the name up to the space.
	while ((cur = *string) && !IsSpacer(cur))
	{
		// Valid string item.
		++string;
		if ((unsigned char)(cur - '0') >= 10)
		{
			// Invalid number, so not an ID.
			break;
		}
		// Still a valid ID, continue collecting.
		id = (id * 10) + (cur - '0');
	}
	*end = string;
	// Don't use extra checks every loop any more, just one will do.
	if (!IsSpacer(cur))
	{
		// Invalid ID, just find the end of the name.
		FindSpacer(end);
		// Save the ID as too large for detection later.
		id = g_iTrueMax;
	}
	return id;
}

int
	GetDecValue(char ** const input)
{
	char *
		str = *input;
	int
		val = 0;
	unsigned char
		cur;
	// Convert to a number and test it.
	while ((cur = (unsigned char)(*str - '0')) < 10)
	{
		// Update the current value.
		val = (val * 10) + cur;
		// Update the current pointer.
		++str;
	}
	// Save the pointer.
	*input = str;
	// Covert the sign and return without an if.
	return val;
}

int
	GetDec(char ** const input)
{
	char *
		str = *input;
	int
		neg = 1;
	switch (*str)
	{
	case '-':
		neg = -1;
		// FALLTHROUGH
	case '+':
		// Check there is valid data after
		if (((unsigned char)(*(++str) - '0')) >= 10)
		{
			// Just return now, the caller will recognise this as bad.
			return 0;
		}
	}
	*input = str;
	return GetDecValue(input) * neg;
}

int
	GetOctValue(char ** const input)
{
	char *
		str = *input;
	int
		val = 0;
	unsigned char
		cur;
	// Convert to a number and test it.
	while ((cur = (unsigned char)(*str - '0')) < 8)
	{
		// Update the current value.
		val = (val * 8) + cur;
		// Update the current pointer.
		++str;
	}
	// Save the pointer.
	*input = str;
	// Covert the sign and return without an if.
	return val;
}

int
	GetOct(char ** const input)
{
	char *
		str = *input;
	int
		neg = 1;
	switch (*str)
	{
	case '-':
		neg = -1;
		// FALLTHROUGH
	case '+':
		// Check there is valid data after
		if (((unsigned char)(*(++str) - '0')) >= 8)
		{
			// Just return now, the caller will recognise this as bad.
			return 0;
		}
	}
	*input = str;
	return GetOctValue(input) * neg;
}

int
	GetHexValue(char ** const input)
{
	char *
		str = *input;
	int
		val = 0;
	// Rewrote it using a big switch.
	for ( ; ; )
	{
		switch (*str)
		{
		case '0':
			val = (val * 16) + 0x00;
			break;
		case '1':
			val = (val * 16) + 0x01;
			break;
		case '2':
			val = (val * 16) + 0x02;
			break;
		case '3':
			val = (val * 16) + 0x03;
			break;
		case '4':
			val = (val * 16) + 0x04;
			break;
		case '5':
			val = (val * 16) + 0x05;
			break;
		case '6':
			val = (val * 16) + 0x06;
			break;
		case '7':
			val = (val * 16) + 0x07;
			break;
		case '8':
			val = (val * 16) + 0x08;
			break;
		case '9':
			val = (val * 16) + 0x09;
			break;
		case 'a':
		case 'A':
			val = (val * 16) + 0x0A;
			break;
		case 'b':
		case 'B':
			val = (val * 16) + 0x0B;
			break;
		case 'c':
		case 'C':
			val = (val * 16) + 0x0C;
			break;
		case 'd':
		case 'D':
			val = (val * 16) + 0x0D;
			break;
		case 'e':
		case 'E':
			val = (val * 16) + 0x0E;
			break;
		case 'f':
		case 'F':
			val = (val * 16) + 0x0F;
			break;
		default:
			// UGLY UGLY UGLY!
			goto sscanf_hex_switch;
		}
		++str;
	}
	// UGLY UGLY UGLY - Needed for the double level break, which isn't native
	// in C.
	sscanf_hex_switch:
	// Save the pointer.
	*input = str;
	// Covert the sign and return without an if.
	return val;
}

int
	GetHex(char ** const input)
{
	char *
		str = *input;
	int
		neg = 1;
	switch (*str)
	{
	case '-':
		neg = -1;
		// FALLTHROUGH
	case '+':
		// Check there is valid data after
		if (((unsigned char)(*(++str) - '0')) >= 10)
		{
			// Just return now, the caller will recognise this as bad.
			return 0;
		}
	}
	if (*str == '0')
	{
		if (*(str + 1) == 'x' || *(str + 1) == 'X')
		{
			// Check there is real data, otherwise it's bad.
			str += 2;
			if ((*str < '0') || ((*str > '9') && ((*str | 0x20) < 'a')) || ((*str | 0x20) > 'f'))
			{
				*input = str - 1;
				return 0;
			}
		}
	}
	else if ((*str < '0') || ((*str > '9') && ((*str | 0x20) < 'a')) || ((*str | 0x20) > 'f'))
	{
		*input = str;
		return 0;
	}
	*input = str;
	return GetHexValue(input) * neg;
	// Convert to a number and test it.  Horribly manually optimised - one of
	// these days I'll try write an ASM hex reader and see how well that works.
	// Actually I think I have written one before, but I don't know where it is
	// and I'm not sure how optimised it is.  Anyway, this version works and
	// avoids loads of big switches (although a single large switch may be more
	// efficient thinking about it).
	/*while ((cur = (unsigned char)((*str | 0x20) - '0')))
	{
		if (cur < 10)
		{
			// 0 - 9
			val = (val * 16) + cur;
		}
		else
		{
			cur -= ('a' - '0');
			if (cur < 6)
			{
				// A - F, a - f
				val = (val * 16) + cur + 10;
			}
			else
			{
				// End of the number.
				// Save the pointer.
				*input = str;
				// Covert the sign and return without an if.
				return val * neg;
			}
		}
	}*/
}

unsigned int
	GetColour(char ** const input, int * type, unsigned int alpha)
{
	char *
		str = *input;
	char
		prefix = 0; // `0x` = 1, `{` = 2, `#` = 3, `` = 4, invalid = 0.
	*type = 0; // Reset.
	switch (*str)
	{
	case '0':
		if (*(str + 1) == 'x' || *(str + 1) == 'X')
		{
			// Check there is real data, otherwise it's bad.
			str += 2;
			if ((*str < '0') || ((*str > '9') && ((*str | 0x20) < 'a')) || ((*str | 0x20) > 'f'))
			{
				*input = str - 1;
				return 0;
			}
			prefix = 1;
		}
		else
		{
			prefix = 4;
		}
		break;
	case '{':
		prefix = 2;
		++str;
		break;
	case '#':
		prefix = 3;
		++str;
		break;
	default:
		prefix = 4;
		break;
	}
	// Get the underlying HEX value.
	*input = str;
	unsigned int ret = (unsigned int)GetHexValue(input);
	// Determine the input type.
	switch (prefix)
	{
	case 1:
		if (*input - str == 6)
		{
			*type = 4;
		}
		else if (*input - str == 8)
		{
			*type = 32;
			return ret;
		}
		break;
	case 2:
		if (*input - str == 6 && **input == '}')
		{
			++(*input);
			*type = 16;
		}
		break;
	case 3:
		if (*input - str == 3)
		{
			// Expand out the 3-digit values to 6-digit values.
			ret =
				((ret >>  8 & 0x00F) * 0x011 << 16) |
				((ret >>  4 & 0x00F) * 0x011 <<  8) |
				((ret >>  0 & 0x00F) * 0x011 <<  0)	;
			*type = 1;
		}
		else if (*input - str == 6)
		{
			*type = 2;
		}
		break;
	case 4:
		if (*input - str == 6)
		{
			*type = 8;
		}
		else if (*input - str == 8)
		{
			*type = 64;
			return ret;
		}
		break;
	}
	// Add in the alpha.
	if (alpha & 0xFFFFFF00)
	{
		// ARGB.
		return ret | (alpha << 24);
	}
	else
	{
		// RGBA.
		return (ret << 8) | alpha;
	}
}

unsigned int
	GetBoolValue(char ** const input)
{
	char *
		str = *input;
	unsigned int
		val = 0;
	for ( ; ; ++str)
	{
		if (*str == '0')
		{
			val <<= 1;
		}
		else if (*str == '1')
		{
			val = (val << 1) | 1;
		}
		else
		{
			break;
		}
	}
	// Save the pointer.
	*input = str;
	return val;
}

int
	GetBool(char ** const input)
{
	char *
		str = *input;
	if (*str == '0')
	{
		if (*(str + 1) == 'b' || *(str + 1) == 'B')
		{
			// Check there is real data, otherwise it's bad.
			str += 2;
			if (*str != '0' && *str != '1')
			{
				*input = str - 1;
				return 0;
			}
		}
	}
	else if (*str != '1')
	{
		*input = str;
		return 0;
	}
	*input = str;
	return (int)GetBoolValue(input);
}

int
	GetNumber(char ** const input)
{
	char *
		str = *input;
	int
		neg = 1;
	switch (*str)
	{
	case '-':
		neg = -1;
		// FALLTHROUGH
	case '+':
		// Check there is valid data after
		if (((unsigned char)(*(++str) - '0')) >= 10)
		{
			// Just return now, the caller will recognise this as bad.
			return 0;
		}
	}
	if (*str == '0')
	{
		++str;
		switch (*str)
		{
		case 'x':
		case 'X':
			// Hex.
			++str;
			if (((*str >= '0') && (*str <= '9')) || (((*str | 0x20) >= 'a') && ((*str | 0x20) <= 'f')))
			{
				*input = str;
				return GetHexValue(input) * neg;
			}
			else
			{
				*input = str - 1;
				return 0;
			}
		case 'b':
		case 'B':
			// Bool.
			if (neg == -1)
			{
				// Can't have negative booleans.
				*input = str;
				return 0;
			}
			else
			{
				++str;
				if ((*str == '0') || (*str == '1'))
				{
					*input = str;
					return (int)GetBoolValue(input);
				}
				else
				{
					*input = str - 1;
					return 0;
				}
			}
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
			// Octal.
			*input = str;
			return GetOctValue(input) * neg;
		case '8':
		case '9':
			// Decimal.
			break;
		default:
			*input = str;
			return 0;
		}
	}
	else if ((*str < '0') || (*str > '9'))
	{
		*input = str;
		return 0;
	}
	*input = str;
	return GetDecValue(input) * neg;
}

bool
	GetLogical(char ** const input)
{
	// Boolean values - if it's not '0' or 'false' then it's  true.  I could be
	// strict and make them enter '1' or 'true' for true, but I'm not going to.
	char *
		string = *input;
	bool
		ret;
	switch (*string++)
	{
	case '0':
		// Set the value to false till we decide otherwise.
		ret = false;
		while (!IsSpacer(*string))
		{
			if (*string++ != '0')
			{
				// Value is not false, thus is true.
				ret = true;
				break;
			}
		}
		// Let the cleanup code below handle excess chars.
		break;
	case 'f':
	case 'F':
		// Default to true unless we find "false" exactly.
		ret = true;
		if ((*string | 0x20) == 'a')
		{
			++string;
			if ((*string | 0x20) == 'l')
			{
				++string;
				if ((*string | 0x20) == 's')
				{
					++string;
					if ((*string | 0x20) == 'e')
					{
						++string;
						// We have the whole word, check that it's on its
						// own, i.e. is followed by a space or delimiter
						// Note that if your delimiter is in the word
						// "false", you may have a bug here but they seem
						// like odd delimiter characters tbh.
						if (IsSpacer(*string))
						{
							// Exact word found on its own so save the fact
							// that it's false.
							ret = false;
						}
					}
				}
			}
		}
		break;
	default:
		ret = true;
		break;
	}
	// Skip the rest of the data.
	*input = string;
	FindSpacer(input);
	return ret;
}

bool
	FindDefaultStart(char ** const str)
{
	// Skip the default value passed for optional parameters.
	if (**str == '(')
	{
		++(*str);
		SkipWhitespace(str);
		return true;
	}
	else
	{
		SscanfWarning(12, "No default value found.");
	}
	return false;
}

bool
	SkipDefault(char ** const str)
{
	bool escape = false;
	int defaultArgs = 0;
	if (FindDefaultStart(str))
	{
		// Default value found - skip it.
		for ( ; ; )
		{
			switch (**str)
			{
			case '\0':
				SscanfWarning(11, "Unclosed default value.");
				return defaultArgs != 0;
			case '\\':
				escape = !escape;
				break;
			case '*':
				if (escape)
				{
					escape = false;
				}
				else
				{
					// Got a variable default.
					++defaultArgs;
				}
				break;
			case ')':
				if (escape)
				{
					escape = false;
				}
				else
				{
					// Current pointer points to the close bracket, skip it.
					++(*str);
					return defaultArgs != 0;
				}
				break;
			default:
				escape = false;
				break;
			}
			++(*str);
		}
	}
	return 0;
}

void
	SkipLength(char ** const input)
{
	// Get an easy pointer to the data to manipulate.
	char *
		str = *input;
	if (*str == '[')
	{
		while (*(++str))
		{
			// Don't check the length is valid, that's effort and slow.
			if (*str == ']')
			{
				*input = str + 1;
				return;
			}
		}
		// If we get here then the end of the string was reached before the
		// valid end of the length.
		*input = str;
		SscanfWarning(15, "Missing string length end.");
	}
	else
	{
		SscanfError(19, "String/array must include a length, please add a destination size.");
	}
}

int
	GetLength(char ** const input, struct args_s & args)
{
	if (**input == '[')
	{
		++(*input);
		char *
			str;
		int
			length;
		bool
			bracketed = false;
		if (**input == '(')
		{
			++(*input);
			bracketed = true;
		}
		if (**input == '*')
		{
			// Length loaded from a parameter.
			str = *input + 1;
			length = *args.Next();
			if (length <= 0)
			{
				length = 1;
				SscanfError(17, "Invalid data length.");
			}
		}
		else
		{
			length = GetDec(input);
			str = *input;
			if (length <= 0)
			{
				length = 1;
				SscanfError(17, "Invalid data length.");
			}
		}
		if (bracketed && *str == ')')
		{
			++str;
		}
		if (*str == ']')
		{
			// Valid end: [number]
			*input = str + 1;
			return length;
		}
		else if (*str)
		{
			// Invalid character: [numberX]
			SscanfWarning(18, "Invalid character in data length.");
			// Loop through the string till we find an end to the size.
			while (*(++str))
			{
				if (*str == ']')
				{
					// Valid end: [numberX]
					*input = str + 1;
					return length;
				}
			}
		}
		// Invalid end: [number
		SscanfWarning(16, "Missing length end.");
		*input = str;
		return length;
	}
	else
	{
		SscanfError(19, "String/array must include a length, please add a destination size.");
		return 1;
	}
}

void
	GetJaggedSlot(cell * start, int count, int size, int slot, cell ** rs, int * rl)
{
	// Get the start of the real data after the header.
	cell
		* cur,
		* dstart = start + count,
		* dend = dstart + (count * size),
		* sstart = (cell *)((char *)(start + slot) + *(start + slot));
	while (start < dstart)
	{
		cur = (cell *)((char *)start + *start);
		// Use "<" not "<=" to avoid assigning 0-length slots.
		if (sstart < cur && cur < dend)
		{
			dend = cur;
		}
		// else if (sstart == cur && slot--)
		//{
		//	// Do something about the fact that we just found a 0 length slot.
		//}
		++start;
	}
	*rs = sstart;
	*rl = (int)(dend - sstart);
}

float
	GetSimilarity(char const * string1, char const * string2)
{
	char matrix[36 * 36];
	memset(matrix, 0, 36 * 36);
	int
		pair = 0,
		ch,
		unique1 = 0,
		unique2 = 0,
		ngrams1 = -1,
		ngrams2 = -1;
	while ((ch = *string1++))
	{
		// This code only looks at numbers and letters, and ignores case.
		if ('0' <= ch && ch <= '9')
		{
			pair = (pair / 36) | ((ch - '0') * 36);
		}
		else if ('a' <= ch && ch <= 'z')
		{
			pair = (pair / 36) | ((ch - ('a' - 10)) * 36);
		}
		else if ('A' <= ch && ch <= 'Z')
		{
			pair = (pair / 36) | ((ch - ('A' - 10)) * 36);
		}
		else
		{
			// Not a character we are interested in.
			continue;
		}
		if (++ngrams1)
		{
			// Got at least TWO characters.
			++matrix[pair];
			++unique1;
		}
	}
	while ((ch = *string2++))
	{
		// This code only looks at numbers and letters, and ignores case.
		if ('0' <= ch && ch <= '9')
		{
			pair = (pair / 36) | ((ch - '0') * 36);
		}
		else if ('a' <= ch && ch <= 'z')
		{
			pair = (pair / 36) | ((ch - ('a' - 10)) * 36);
		}
		else if ('A' <= ch && ch <= 'Z')
		{
			pair = (pair / 36) | ((ch - ('A' - 10)) * 36);
		}
		else
		{
			// Not a character we are interested in.
			continue;
		}
		// The original version had positive numbers as pairs in `string1` but
		// not `string2`, and vice-versa, but I already optimised it.
		if (!++ngrams2)
		{
		}
		else if (matrix[pair])
		{
			--matrix[pair];
			--unique1;
		}
		else
		{
			++unique2;
		}
	}
	if (ngrams1 < 1 || ngrams2 < 1)
	{
		// There just aren't enough letters to compare.
		return 0;
	}
	// Normalise the number of matching pairs and multiply.
	return (1.0f - ((float)unique1 / (float)ngrams1)) * (1.0f - ((float)unique2 / (float)ngrams2));
}

