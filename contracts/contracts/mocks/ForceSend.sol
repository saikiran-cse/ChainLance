// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @dev TEST ONLY. Pushes its ETH into any address through selfdestruct, bypassing receive() checks.
///      The compiler warns that selfdestruct is deprecated; that is expected for this mock.
contract ForceSend {
    constructor(address payable target) payable {
        selfdestruct(target);
    }
}
