package dn.demedallo.admin.model;

import dn.demedallo.admin.service.ChannelUsageService.ChannelTech;

/**
 * One Asterisk channel leg active during part of a call (from CDR channel or dstchannel).
 */
public record CdrChannelLeg(long startEpochSec, long endEpochSec, ChannelTech tech) {
}
